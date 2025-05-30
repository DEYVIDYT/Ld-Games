package com.LDGAMES.activities;

import android.content.Context;
import android.content.SharedPreferences; // Added import
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // Added import
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.LDGAMES.R;
import com.LDGAMES.utils.DynamicThemeManager;
import com.LDGAMES.utils.HydraApiManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class ApiManagementActivity extends AppCompatActivity {

    private static final String TAG = "ApiManagementActivity"; // Added TAG
    private static final String API_ENABLED_PREFS = "api_enabled_prefs"; // Added constant

    private RecyclerView rvApiUrls;
    private FloatingActionButton fabAddApi;
    private HydraApiManager hydraApiManager;
    private ApiUrlAdapter apiUrlAdapter;
    private List<String> apiUrls = new ArrayList<>();
    private SharedPreferences apiEnabledPrefs; // Added SharedPreferences instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dynamic theme before inflating layout
        DynamicThemeManager.getInstance().applyDynamicColors(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_management);

        // Initialize HydraApiManager
        hydraApiManager = HydraApiManager.getInstance(this);

        // Initialize SharedPreferences for API enabled status
        apiEnabledPrefs = getSharedPreferences(API_ENABLED_PREFS, Context.MODE_PRIVATE);

        // Configure toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize UI components
        rvApiUrls = findViewById(R.id.rv_api_urls);
        fabAddApi = findViewById(R.id.fab_add_api);

        // Configure RecyclerView for APIs
        setupApiUrlsRecyclerView();

        // Configure listeners
        setupListeners();
    }

    private void setupApiUrlsRecyclerView() {
        rvApiUrls.setLayoutManager(new LinearLayoutManager(this));
        apiUrls = hydraApiManager.getApiUrls();
        apiUrlAdapter = new ApiUrlAdapter(apiUrls);
        rvApiUrls.setAdapter(apiUrlAdapter);
    }

    private void setupListeners() {
        // Listener for FAB to add API
        fabAddApi.setOnClickListener(v -> {
            showAddApiDialog();
        });
    }

    private void showAddApiDialog() {
        // Inflate dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_api, null);
        TextInputLayout tilApiUrl = dialogView.findViewById(R.id.til_api_url);
        TextInputEditText etApiUrl = dialogView.findViewById(R.id.et_api_url);

        // Create and show dialog
        new MaterialAlertDialogBuilder(this)
            .setTitle("Adicionar Nova API")
            .setView(dialogView)
            .setPositiveButton("Adicionar", (dialog, which) -> {
                String apiUrl = etApiUrl.getText().toString().trim();
                if (!TextUtils.isEmpty(apiUrl)) {
                    if (apiUrl.startsWith("http://") || apiUrl.startsWith("https://")) {
                        if (hydraApiManager.addApiUrl(apiUrl)) {
                            // Update list
                            apiUrls.clear();
                            apiUrls.addAll(hydraApiManager.getApiUrls());
                            apiUrlAdapter.notifyDataSetChanged();
                            // Set the newly added API as enabled by default
                            apiEnabledPrefs.edit().putBoolean(apiUrl, true).apply();
                            Toast.makeText(this, "API adicionada com sucesso", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Esta API já está na lista", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "A URL deve começar com http:// ou https://", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Digite uma URL válida", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Adapter for the list of API URLs
     */
    private class ApiUrlAdapter extends RecyclerView.Adapter<ApiUrlAdapter.ApiUrlViewHolder> {

        private List<String> urls;

        public ApiUrlAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public ApiUrlViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_api_url, parent, false);
            return new ApiUrlViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ApiUrlViewHolder holder, int position) {
            String url = urls.get(position);
            holder.tvApiName.setText(getApiNameFromUrl(url));
            holder.tvApiUrl.setText(url);

            // Load the saved state for the switch
            boolean isEnabled = apiEnabledPrefs.getBoolean(url, true); // Default to true if not found
            holder.switchApiActive.setChecked(isEnabled);
            Log.d(TAG, "Setting switch for " + url + " to " + isEnabled);


            // Listener for the activation switch
            holder.switchApiActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Save the state to SharedPreferences
                apiEnabledPrefs.edit().putBoolean(url, isChecked).apply();
                Log.d(TAG, "Saved state for " + url + ": " + isChecked);
                Toast.makeText(
                    ApiManagementActivity.this,
                    isChecked ? "API ativada: " + getApiNameFromUrl(url) : "API desativada: " + getApiNameFromUrl(url),
                    Toast.LENGTH_SHORT
                ).show();
            });

            // Configure remove button
            holder.btnRemoveApi.setOnClickListener(v -> {
                // Confirm removal
                new MaterialAlertDialogBuilder(ApiManagementActivity.this)
                        .setTitle("Remover API")
                        .setMessage("Deseja remover esta API da lista?")
                        .setPositiveButton("Sim", (dialog, which) -> {
                            if (hydraApiManager.removeApiUrl(url)) {
                                // Also remove the preference for this API
                                apiEnabledPrefs.edit().remove(url).apply();
                                Log.d(TAG, "Removed preference for " + url);

                                // Update the list in the adapter directly
                                int currentPosition = holder.getAdapterPosition(); // Use getAdapterPosition for safety
                                if (currentPosition != RecyclerView.NO_POSITION) {
                                    urls.remove(currentPosition);
                                    notifyItemRemoved(currentPosition);
                                    // Optional: notifyItemRangeChanged if positions shift significantly
                                    // notifyItemRangeChanged(currentPosition, urls.size());
                                }
                                Toast.makeText(ApiManagementActivity.this, "API removida com sucesso", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Não", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        /**
         * Extracts the API name from the URL
         */
        private String getApiNameFromUrl(String url) {
            try {
                // Extract filename from URL
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                // Remove .json extension
                if (fileName.endsWith(".json")) {
                    fileName = fileName.substring(0, fileName.length() - 5);
                }
                // Replace hyphens with spaces and capitalize
                fileName = fileName.replace('-', ' ');
                StringBuilder result = new StringBuilder();
                boolean capitalizeNext = true;
                for (char c : fileName.toCharArray()) {
                    if (Character.isSpaceChar(c)) {
                        result.append(c);
                        capitalizeNext = true;
                    } else if (capitalizeNext) {
                        result.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        result.append(c);
                    }
                }
                return result.toString();
            } catch (Exception e) {
                return "API Desconhecida";
            }
        }

        class ApiUrlViewHolder extends RecyclerView.ViewHolder {
            TextView tvApiName;
            TextView tvApiUrl;
            SwitchCompat switchApiActive;
            MaterialButton btnRemoveApi;

            public ApiUrlViewHolder(@NonNull View itemView) {
                super(itemView);
                tvApiName = itemView.findViewById(R.id.tv_api_name);
                tvApiUrl = itemView.findViewById(R.id.tv_api_url);
                switchApiActive = itemView.findViewById(R.id.switch_api_active);
                btnRemoveApi = itemView.findViewById(R.id.btn_remove_api);
            }
        }
    }
}

