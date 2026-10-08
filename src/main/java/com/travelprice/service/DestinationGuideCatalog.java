package com.travelprice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DestinationGuideCatalog {
    public record Card(String title, String body, String tip) {}
    public record Source(String label, String url) {}
    public record Guide(String slug, String name, String region, String scene, String lead,
                        String about, List<Card> highlights, List<Card> food, List<String> route,
                        String location, String visit, List<Card> budget, List<Source> sources,
                        String checkedAt) {}
    private final Map<String, Guide> guides;

    public DestinationGuideCatalog(ObjectMapper mapper) throws IOException {
        var loaded = new LinkedHashMap<String, Guide>();
        try (var input = new ClassPathResource("data/destination-guides.json").getInputStream()) {
            List<Guide> entries = mapper.readValue(input, new TypeReference<List<Guide>>() {});
            for (var guide : entries) {
                if (loaded.putIfAbsent(guide.slug(), guide) != null)
                    throw new IllegalStateException("Duplicate destination guide: " + guide.slug());
            }
        }
        guides = Map.copyOf(loaded);
    }

    public Optional<Guide> find(String slug) { return Optional.ofNullable(guides.get(slug)); }
}
