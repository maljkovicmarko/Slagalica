package com.example.slagalica.Services;

import com.example.slagalica.Model.Region;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class RegionService {
    public interface RegionsCallback {
        void onSuccess(List<Region> regions);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseFirestore db;

    public RegionService() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadActiveRegions(RegionsCallback onSuccess, FailureCallback onFailure) {
        db.collection("regions")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Region> regions = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Region region = document.toObject(Region.class);
                        if (region == null) {
                            continue;
                        }
                        if (isBlank(region.getRegionId())) {
                            region.setRegionId(document.getId());
                        }
                        if (isBlank(region.getDisplayName())) {
                            region.setDisplayName(region.getRegionId());
                        }
                        regions.add(region);
                    }

                    if (regions.isEmpty()) {
                        regions.addAll(defaultRegions());
                    }

                    regions.sort((first, second) -> {
                        int byOrder = Integer.compare(first.getSortOrder(), second.getSortOrder());
                        if (byOrder != 0) {
                            return byOrder;
                        }
                        return first.toString().compareToIgnoreCase(second.toString());
                    });
                    onSuccess.onSuccess(regions);
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage() == null
                        ? "Nije moguće učitati regione."
                        : e.getMessage()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<Region> defaultRegions() {
        List<Region> regions = new ArrayList<>();
        regions.add(new Region("beograd", "Beograd", "bridge", new GeoPoint(44.8125, 20.4612), 12, 10, true));
        regions.add(new Region("vojvodina", "Vojvodina", "field", new GeoPoint(45.2671, 19.8335), 55, 20, true));
        regions.add(new Region("sumadija_zapadna_srbija", "Šumadija i Zapadna Srbija", "forest", new GeoPoint(43.8914, 20.3497), 65, 30, true));
        regions.add(new Region("juzna_istocna_srbija", "Južna i Istočna Srbija", "mountain", new GeoPoint(43.3209, 21.8958), 75, 40, true));
        regions.add(new Region("kosovo_metohija", "Kosovo i Metohija", "fortress", new GeoPoint(42.6026, 20.9030), 55, 50, true));
        return regions;
    }
}
