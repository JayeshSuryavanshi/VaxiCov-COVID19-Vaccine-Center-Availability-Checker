package com.vaxicov;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vaxicov.adapters.CenterAdapter;
import com.vaxicov.data.SlotProviders;
import com.vaxicov.data.SlotRepository;
import com.vaxicov.domain.AgeGroup;
import com.vaxicov.domain.DoseType;
import com.vaxicov.domain.SearchQuery;
import com.vaxicov.domain.SlotFilter;
import com.vaxicov.notifier.AvailabilityNotifier;
import com.vaxicov.notifier.SlotNotifierScheduler;
import com.vaxicov.pojo.Center;
import com.vaxicov.pojo.District;
import com.vaxicov.pojo.State;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The app's single screen: pick an area, dose, vaccine and age group,
 * search for bookable sessions, and keep a watchlist of areas that are
 * checked in the background.
 */
public class MainActivity extends AppCompatActivity implements CenterAdapter.Listener {

    private static final String TAG = "MainActivity";
    private static final String STATE_AGE_GROUP = "age_group";
    private static final String STATE_DOSE = "dose";
    private static final String STATE_VACCINE = "vaccine";

    private AppPreferences preferences;
    private SlotRepository repository;

    private RadioGroup findBy;
    private EditText etPin;
    private EditText etAgeGroup;
    private EditText etDose;
    private EditText etVaccine;
    private AutoCompleteTextView etState;
    private AutoCompleteTextView etDistrict;
    private Button btnSearch;
    private Button btnNotify;
    private View watchlistSection;
    private LinearLayout watchRows;
    private TextView tvSummary;
    private TextView tvEmpty;
    private ProgressBar progress;
    private RecyclerView rvCenters;
    private CenterAdapter adapter;

    private final List<State> states = new ArrayList<>();
    private final List<District> districts = new ArrayList<>();
    @Nullable private State selectedState;
    @Nullable private District selectedDistrict;
    @NonNull private AgeGroup ageGroup = AgeGroup.ALL;
    @NonNull private DoseType dose = DoseType.ANY;
    @NonNull private String vaccine = SearchQuery.ANY_VACCINE;

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
            setDose(DoseType.fromLabel(savedInstanceState.getString(STATE_DOSE)));
            setVaccine(savedInstanceState.getString(STATE_VACCINE));
        } else {
            setAgeGroup(AgeGroup.ALL);
            setDose(DoseType.ANY);
            setVaccine(SearchQuery.ANY_VACCINE);
            if (!handleNotificationIntent(getIntent())) {
                SearchQuery last = preferences.getLastSearch();
                if (last != null) {
                    prefill(last);
                }
            }
        }
        loadStates();
        renderWatchlist();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /** Fills the form from the watch a notification was about and searches it. */
    private boolean handleNotificationIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(AvailabilityNotifier.EXTRA_FROM_NOTIFICATION, false)) {
            return false;
        }
        List<SearchQuery> watches = preferences.getWatches();
        int index = intent.getIntExtra(AvailabilityNotifier.EXTRA_WATCH_INDEX, -1);
        SearchQuery query = index >= 0 && index < watches.size() ? watches.get(index)
                : watches.isEmpty() ? null : watches.get(0);
        if (query == null) {
            return false;
        }
        prefill(query);
        search();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_AGE_GROUP, ageGroup.label());
        outState.putString(STATE_DOSE, dose.label());
        outState.putString(STATE_VACCINE, vaccine);
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
            SlotNotifierScheduler.clearWatches(this);
            renderWatchlist();
            Toast.makeText(this, R.string.toast_alerts_stopped, Toast.LENGTH_SHORT).show();
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
        etDose = findViewById(R.id.et_dose);
        etVaccine = findViewById(R.id.et_vaccine);
        etState = findViewById(R.id.et_state);
        etDistrict = findViewById(R.id.et_district);
        btnSearch = findViewById(R.id.btn_search);
        btnNotify = findViewById(R.id.btn_notify);
        watchlistSection = findViewById(R.id.ll_watchlist);
        watchRows = findViewById(R.id.ll_watches);
        tvSummary = findViewById(R.id.tv_summary);
        tvEmpty = findViewById(R.id.tv_empty);
        progress = findViewById(R.id.progressbar);
        rvCenters = findViewById(R.id.rv_centers);

        adapter = new CenterAdapter(this);
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
        etDose.setOnClickListener(this::showDoseMenu);
        etVaccine.setOnClickListener(this::showVaccineMenu);

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
        btnNotify.setOnClickListener(v -> confirmWatch());
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
                // A prefilled district search only knows the state by name; resolve it now.
                if (selectedState != null && selectedState.getStateId() == null && selectedState.getStateName() != null) {
                    State resolved = findState(selectedState.getStateName());
                    if (resolved != null) {
                        selectedState = resolved;
                        if (resolved.getStateId() != null) {
                            loadDistricts(resolved.getStateId());
                        }
                    }
                }
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

    // ---- Filters -----------------------------------------------------------

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

    private void showDoseMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.dose_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            setDose(DoseType.fromLabel(String.valueOf(item.getTitle())));
            return true;
        });
        menu.show();
    }

    private void setDose(@NonNull DoseType type) {
        dose = type;
        etDose.setText(type.label());
    }

    private void showVaccineMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.vaccine_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            setVaccine(String.valueOf(item.getTitle()));
            return true;
        });
        menu.show();
    }

    private void setVaccine(@Nullable String name) {
        vaccine = name == null || name.trim().isEmpty() ? SearchQuery.ANY_VACCINE : name.trim();
        etVaccine.setText(vaccine);
    }

    // ---- Query & search ----------------------------------------------------

    /** Builds the query from the form, or returns {@code null} after telling the user what is missing. */
    @Nullable
    private SearchQuery buildQuery() {
        if (findBy.getCheckedRadioButtonId() == R.id.findbypin) {
            String pin = etPin.getText().toString().trim();
            if (!SearchQuery.isValidPincode(pin)) {
                Toast.makeText(this, R.string.error_pincode, Toast.LENGTH_SHORT).show();
                return null;
            }
            return SearchQuery.byPin(Integer.parseInt(pin), ageGroup).withFilters(dose, vaccine);
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
                selectedDistrict.getDistrictName(), ageGroup).withFilters(dose, vaccine);
    }

    private void search() {
        SearchQuery query = buildQuery();
        if (query == null) {
            return;
        }
        preferences.setLastSearch(query);
        setLoading(true);
        repository.search(query, new SlotRepository.Callback<List<Center>>() {
            @Override
            public void onSuccess(@NonNull List<Center> result) {
                setLoading(false);
                adapter.setCenters(result);
                showSummary(result);
                showEmpty(result.isEmpty() ? getString(R.string.no_slots, query.describeArea()) : null);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "Search failed for " + query, error);
                setLoading(false);
                adapter.clear();
                tvSummary.setVisibility(View.GONE);
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

    private void showSummary(@NonNull List<Center> centers) {
        if (centers.isEmpty()) {
            tvSummary.setVisibility(View.GONE);
            return;
        }
        int doses = SlotFilter.totalAvailable(centers);
        String centersText = getResources().getQuantityString(R.plurals.summary_centers, centers.size(), centers.size());
        String dosesText = getResources().getQuantityString(R.plurals.summary_doses, doses, doses);
        String time = DateFormat.getTimeFormat(this).format(new Date());
        tvSummary.setText(getString(R.string.summary_format, centersText, dosesText, time));
        tvSummary.setVisibility(View.VISIBLE);
    }

    private void showEmpty(@Nullable String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void clearResults() {
        adapter.clear();
        showEmpty(null);
        tvSummary.setVisibility(View.GONE);
        districts.clear();
        selectedState = null;
        selectedDistrict = null;
        etState.setText("");
        etDistrict.setText("");
    }

    /** Puts a saved query back into the form. */
    private void prefill(@NonNull SearchQuery query) {
        setAgeGroup(query.getAgeGroup());
        setDose(query.getDose());
        setVaccine(query.getVaccine());
        if (query.isByPin()) {
            findBy.check(R.id.findbypin);
            etPin.setText(String.valueOf(query.getPincode()));
        } else {
            findBy.check(R.id.findbydistrict);
            selectedState = findState(query.getStateName() == null ? "" : query.getStateName());
            if (selectedState == null) {
                selectedState = new State(null, query.getStateName());
            }
            District district = new District();
            district.setDistrictId(query.getDistrictId());
            district.setDistrictName(query.getDistrictName());
            selectedDistrict = district;
            etState.setText(query.getStateName());
            etDistrict.setText(query.getDistrictName());
        }
    }

    // ---- Center actions ----------------------------------------------------

    @Override
    public void onOpenMap(@NonNull Center center) {
        String where = center.getName() == null ? "" : center.getName();
        if (center.getAddress() != null && !center.getAddress().trim().isEmpty()) {
            where += ", " + center.getAddress().trim();
        }
        if (center.getPincode() != null) {
            where += " " + center.getPincode();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(where)));
        startExternal(intent);
    }

    @Override
    public void onShare(@NonNull Center center) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                .putExtra(Intent.EXTRA_TEXT, CenterAdapter.shareText(center) + "\n\n" + getString(R.string.share_footer));
        startExternal(Intent.createChooser(intent, getString(R.string.action_share)));
    }

    private void startExternal(@NonNull Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Watchlist ---------------------------------------------------------

    private void confirmWatch() {
        SearchQuery query = buildQuery();
        if (query == null) {
            return;
        }
        if (preferences.getWatches().contains(query)) {
            Toast.makeText(this, R.string.toast_watch_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        if (preferences.getWatches().size() >= AppPreferences.MAX_WATCHES) {
            Toast.makeText(this, getString(R.string.toast_watch_limit, AppPreferences.MAX_WATCHES), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.notifier_dialog_title)
                .setMessage(getString(R.string.notifier_dialog_message, query.describeArea(),
                        query.describeFilters(), AppPreferences.MAX_WATCHES))
                .setPositiveButton(R.string.notifier_dialog_start, (dialog, which) -> {
                    SlotNotifierScheduler.addWatch(this, query);
                    renderWatchlist();
                    Toast.makeText(this, getString(R.string.toast_alerts_started, query.describeArea()), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderWatchlist() {
        List<SearchQuery> watches = preferences.getWatches();
        watchRows.removeAllViews();
        watchlistSection.setVisibility(watches.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SearchQuery watch : watches) {
            View row = inflater.inflate(R.layout.item_watch, watchRows, false);
            TextView label = row.findViewById(R.id.tv_watch);
            label.setText(getString(R.string.watch_format, watch.describeArea(), watch.describeFilters()));
            label.setOnClickListener(v -> {
                prefill(watch);
                search();
            });
            row.findViewById(R.id.btn_remove_watch).setOnClickListener(v -> {
                SlotNotifierScheduler.removeWatch(this, watch);
                renderWatchlist();
                Toast.makeText(this, getString(R.string.toast_watch_removed, watch.describeArea()), Toast.LENGTH_SHORT).show();
            });
            watchRows.addView(row);
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
