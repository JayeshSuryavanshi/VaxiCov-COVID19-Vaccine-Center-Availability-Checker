package com.akshaychavan.vaxicov;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akshaychavan.vaxicov.adapters.CenterAdapter;
import com.akshaychavan.vaxicov.data.SlotProviders;
import com.akshaychavan.vaxicov.data.SlotRepository;
import com.akshaychavan.vaxicov.domain.AgeGroup;
import com.akshaychavan.vaxicov.domain.SearchQuery;
import com.akshaychavan.vaxicov.notifier.AvailabilityNotifier;
import com.akshaychavan.vaxicov.notifier.SlotNotifierScheduler;
import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.District;
import com.akshaychavan.vaxicov.pojo.State;

import java.util.ArrayList;
import java.util.List;

/**
 * The app's single screen: pick an area and age group, search for bookable
 * sessions, and optionally keep a background watch that notifies when new
 * slots open up.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String STATE_AGE_GROUP = "age_group";

    private AppPreferences preferences;
    private SlotRepository repository;

    private RadioGroup findBy;
    private EditText etPin;
    private EditText etAgeGroup;
    private AutoCompleteTextView etState;
    private AutoCompleteTextView etDistrict;
    private Button btnSearch;
    private Button btnNotify;
    private TextView tvStatus;
    private TextView tvEmpty;
    private ProgressBar progress;
    private RecyclerView rvCenters;
    private CenterAdapter adapter;

    private final List<State> states = new ArrayList<>();
    private final List<District> districts = new ArrayList<>();
    @Nullable private State selectedState;
    @Nullable private District selectedDistrict;
    @NonNull private AgeGroup ageGroup = AgeGroup.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = new AppPreferences(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        bindViews();
        bindEvents();
        createRepository();
        AvailabilityNotifier.ensureChannel(this);

        if (savedInstanceState != null) {
            setAgeGroup(AgeGroup.fromLabel(savedInstanceState.getString(STATE_AGE_GROUP)));
        } else {
            setAgeGroup(AgeGroup.ALL);
            SearchQuery watched = preferences.getNotifierQuery();
            if (watched != null) {
                prefill(watched);
                if (getIntent().getBooleanExtra(AvailabilityNotifier.EXTRA_FROM_NOTIFICATION, false)) {
                    search();
                }
            }
        }
        loadStates();
        renderNotifierStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(AvailabilityNotifier.EXTRA_FROM_NOTIFICATION, false)) {
            SearchQuery watched = preferences.getNotifierQuery();
            if (watched != null) {
                prefill(watched);
                search();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_AGE_GROUP, ageGroup.label());
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.shutdown();
        }
        super.onDestroy();
    }

    // ---- Menu --------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_sample_data).setChecked(preferences.isSampleDataEnabled());
        menu.findItem(R.id.action_stop_alerts).setVisible(SlotNotifierScheduler.isActive(this));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sample_data) {
            boolean enabled = !preferences.isSampleDataEnabled();
            preferences.setSampleDataEnabled(enabled);
            item.setChecked(enabled);
            createRepository();
            clearResults();
            loadStates();
            Toast.makeText(this, enabled ? R.string.toast_sample_data_on : R.string.toast_sample_data_off,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_stop_alerts) {
            stopAlerts();
            return true;
        }
        if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- Setup -------------------------------------------------------------

    private void bindViews() {
        findBy = findViewById(R.id.rg_findby);
        etPin = findViewById(R.id.et_pin);
        etAgeGroup = findViewById(R.id.et_agegroup);
        etState = findViewById(R.id.et_state);
        etDistrict = findViewById(R.id.et_district);
        btnSearch = findViewById(R.id.btn_search);
        btnNotify = findViewById(R.id.btn_notify);
        tvStatus = findViewById(R.id.tv_notifier_status);
        tvEmpty = findViewById(R.id.tv_empty);
        progress = findViewById(R.id.progressbar);
        rvCenters = findViewById(R.id.rv_centers);

        adapter = new CenterAdapter();
        rvCenters.setLayoutManager(new LinearLayoutManager(this));
        rvCenters.setAdapter(adapter);
    }

    private void bindEvents() {
        findBy.setOnCheckedChangeListener((group, checkedId) -> {
            boolean byPin = checkedId == R.id.findbypin;
            etPin.setVisibility(byPin ? View.VISIBLE : View.GONE);
            etState.setVisibility(byPin ? View.GONE : View.VISIBLE);
            etDistrict.setVisibility(byPin ? View.GONE : View.VISIBLE);
        });

        etAgeGroup.setOnClickListener(this::showAgeGroupMenu);

        etState.setOnItemClickListener((parent, view, position, id) -> {
            selectedState = findState(etState.getText().toString());
            selectedDistrict = null;
            etDistrict.setText("");
            districts.clear();
            if (selectedState != null && selectedState.getStateId() != null) {
                loadDistricts(selectedState.getStateId());
            }
        });

        etDistrict.setOnItemClickListener((parent, view, position, id) ->
                selectedDistrict = findDistrict(etDistrict.getText().toString()));

        btnSearch.setOnClickListener(v -> search());
        btnNotify.setOnClickListener(v -> {
            if (SlotNotifierScheduler.isActive(this)) {
                stopAlerts();
            } else {
                confirmAlerts();
            }
        });
    }

    private void createRepository() {
        if (repository != null) {
            repository.shutdown();
        }
        repository = new SlotRepository(SlotProviders.current(preferences));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(getString(R.string.data_source_format, repository.provider().name()));
        }
    }

    // ---- Locations ---------------------------------------------------------

    private void loadStates() {
        repository.loadStates(new SlotRepository.Callback<List<State>>() {
            @Override
            public void onSuccess(@NonNull List<State> result) {
                states.clear();
                states.addAll(result);
                etState.setAdapter(new ArrayAdapter<>(MainActivity.this, R.layout.dropdown_item, names(result)));
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "Could not load states", error);
                Toast.makeText(MainActivity.this, R.string.error_states, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDistricts(int stateId) {
        repository.loadDistricts(stateId, new SlotRepository.Callback<List<District>>() {
            @Override
            public void onSuccess(@NonNull List<District> result) {
                districts.clear();
                districts.addAll(result);
                etDistrict.setAdapter(new ArrayAdapter<>(MainActivity.this, R.layout.dropdown_item, districtNames(result)));
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "Could not load districts", error);
                Toast.makeText(MainActivity.this, R.string.error_districts, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private State findState(@NonNull String name) {
        for (State state : states) {
            if (name.trim().equalsIgnoreCase(state.getStateName())) {
                return state;
            }
        }
        return null;
    }

    @Nullable
    private District findDistrict(@NonNull String name) {
        for (District district : districts) {
            if (name.trim().equalsIgnoreCase(district.getDistrictName())) {
                return district;
            }
        }
        return null;
    }

    private static List<String> names(List<State> states) {
        List<String> names = new ArrayList<>();
        for (State state : states) {
            names.add(state.getStateName());
        }
        return names;
    }

    private static List<String> districtNames(List<District> districts) {
        List<String> names = new ArrayList<>();
        for (District district : districts) {
            names.add(district.getDistrictName());
        }
        return names;
    }

    // ---- Query & search ----------------------------------------------------

    private void showAgeGroupMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.agegroup_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            setAgeGroup(AgeGroup.fromLabel(String.valueOf(item.getTitle())));
            return true;
        });
        menu.show();
    }

    private void setAgeGroup(@NonNull AgeGroup group) {
        ageGroup = group;
        etAgeGroup.setText(group.label());
    }

    /** Builds the query from the form, or returns {@code null} after telling the user what is missing. */
    @Nullable
    private SearchQuery buildQuery() {
        if (findBy.getCheckedRadioButtonId() == R.id.findbypin) {
            String pin = etPin.getText().toString().trim();
            if (!SearchQuery.isValidPincode(pin)) {
                Toast.makeText(this, R.string.error_pincode, Toast.LENGTH_SHORT).show();
                return null;
            }
            return SearchQuery.byPin(Integer.parseInt(pin), ageGroup);
        }
        if (selectedState == null) {
            selectedState = findState(etState.getText().toString());
        }
        if (selectedDistrict == null) {
            selectedDistrict = findDistrict(etDistrict.getText().toString());
        }
        if (selectedState == null) {
            Toast.makeText(this, R.string.error_state, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (selectedDistrict == null || selectedDistrict.getDistrictId() == null) {
            Toast.makeText(this, R.string.error_district, Toast.LENGTH_SHORT).show();
            return null;
        }
        return SearchQuery.byDistrict(selectedDistrict.getDistrictId(), selectedState.getStateName(),
                selectedDistrict.getDistrictName(), ageGroup);
    }

    private void search() {
        SearchQuery query = buildQuery();
        if (query == null) {
            return;
        }
        setLoading(true);
        repository.search(query, new SlotRepository.Callback<List<Center>>() {
            @Override
            public void onSuccess(@NonNull List<Center> result) {
                setLoading(false);
                adapter.setCenters(result);
                showEmpty(result.isEmpty() ? getString(R.string.no_slots, query.describeArea()) : null);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "Search failed for " + query, error);
                setLoading(false);
                adapter.clear();
                showEmpty(getString(R.string.error_network));
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!loading);
        if (loading) {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void showEmpty(@Nullable String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void clearResults() {
        adapter.clear();
        showEmpty(null);
        districts.clear();
        selectedState = null;
        selectedDistrict = null;
        etState.setText("");
        etDistrict.setText("");
    }

    /** Puts a saved notifier query back into the form. */
    private void prefill(@NonNull SearchQuery query) {
        setAgeGroup(query.getAgeGroup());
        if (query.isByPin()) {
            findBy.check(R.id.findbypin);
            etPin.setText(String.valueOf(query.getPincode()));
        } else {
            findBy.check(R.id.findbydistrict);
            selectedState = new State(null, query.getStateName());
            District district = new District();
            district.setDistrictId(query.getDistrictId());
            district.setDistrictName(query.getDistrictName());
            selectedDistrict = district;
            etState.setText(query.getStateName());
            etDistrict.setText(query.getDistrictName());
        }
    }

    // ---- Background alerts -------------------------------------------------

    private void confirmAlerts() {
        SearchQuery query = buildQuery();
        if (query == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.notifier_dialog_title)
                .setMessage(getString(R.string.notifier_dialog_message, query.describeArea(), query.getAgeGroup().label()))
                .setPositiveButton(R.string.notifier_dialog_start, (dialog, which) -> {
                    SlotNotifierScheduler.schedule(this, query);
                    renderNotifierStatus();
                    Toast.makeText(this, R.string.toast_alerts_started, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void stopAlerts() {
        SlotNotifierScheduler.cancel(this);
        renderNotifierStatus();
        Toast.makeText(this, R.string.toast_alerts_stopped, Toast.LENGTH_SHORT).show();
    }

    private void renderNotifierStatus() {
        SearchQuery watched = preferences.getNotifierQuery();
        if (watched != null) {
            tvStatus.setText(getString(R.string.notifier_status_active, watched.describeArea(), watched.getAgeGroup().label()));
            tvStatus.setVisibility(View.VISIBLE);
            btnNotify.setText(R.string.action_stop_alerts);
            btnNotify.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorRed)));
        } else {
            tvStatus.setVisibility(View.GONE);
            btnNotify.setText(R.string.action_notify_me);
            btnNotify.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorGreen)));
        }
        invalidateOptionsMenu();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
