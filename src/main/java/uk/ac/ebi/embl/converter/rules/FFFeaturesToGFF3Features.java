package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.gff3.Gff3GroupedFeatures;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class FFFeaturesToGFF3Features implements IConversionRule<Entry, Gff3GroupedFeatures> {
    Map<String, List<GFF3Feature>> geneMap = new LinkedHashMap<>();

    @Override
    public Gff3GroupedFeatures from(Entry entry) {

        try {
            entry.setPrimaryAccession(entry.getPrimaryAccession() + ".1");
            entry.getSequence().setAccession(entry.getSequence().getAccession() + ".1");

            Map<String, List<ConversionEntry>> featureMap = ConversionUtils.getFFToGFF3FeatureMap();

            for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

                if (feature.getName().equalsIgnoreCase("source")) {
                    continue; // early exit
                }

                // TODO: insert a gene feature if/where appropriate
                Optional<ConversionEntry> first = featureMap.get(feature.getName()).stream()
                        .filter(conversionEntry -> hasAllQualifiers(feature, conversionEntry)).findFirst();

                // Rule: Throw an error if we find an unmapped feature
                if (first.isEmpty())
                    throw new Exception("Mapping not found for " + feature.getName());

                buildGeneFeatureMap(entry.getPrimaryAccession(),feature);

            }
            sortFeaturesAndAssignId();

            return new Gff3GroupedFeatures(geneMap);

        }catch (Exception e){
            throw new ConversionError();
        }
    }


    private void buildGeneFeatureMap(String accession, Feature ffFeature) {

       List<Qualifier> genes = ffFeature.getQualifiers(Qualifier.GENE_QUALIFIER_NAME);
       String source = ".";
       String score = ".";

        try {
            for (Qualifier gene : genes) {

                List<GFF3Feature> gfFeatures = geneMap.getOrDefault(gene.getValue(), new ArrayList<>());
                Map<String, String> attributes = ffFeature.getQualifiers().stream()
                        .filter(q -> !"gene".equals(q.getName())) // gene is filtered for handling overlapping gene
                        .collect(Collectors.toMap(Qualifier::getName, Qualifier::getValue));
                attributes.put("gene", gene.getValue());

                if (!getPartiality(ffFeature).isBlank()) {
                    attributes.put("partial", getPartiality(ffFeature));
                }

                GFF3Feature gfFeature = new GFF3Feature(
                        accession,
                        source,
                        ffFeature.getName(),
                        ffFeature.getLocations().getMinPosition(),
                        ffFeature.getLocations().getMaxPosition(),
                        score,
                        getStrand(ffFeature),
                        getPhase(ffFeature),
                        attributes
                );

                gfFeatures.add(gfFeature);

                geneMap.put(gene.getValue(), gfFeatures);
            }
        }catch (Exception e){
            throw new ConversionError();
        }
    }

    private void sortFeaturesAndAssignId() {
        for( String geneName : geneMap.keySet()) {
            List<GFF3Feature> gfFeatures = geneMap.get(geneName);

            //Sort feature by start and end location
            gfFeatures.sort(Comparator
                    .comparingLong(GFF3Feature::getStart)
                    .thenComparing(GFF3Feature::getEnd, Comparator.reverseOrder()));

            Optional<GFF3Feature> firstFeature = gfFeatures.stream().findFirst();

            // Set ID and Parent
            if (firstFeature.isPresent()) {

                // Set ID for root
                String idValue = "%s_%s".formatted(firstFeature.get().getName(), geneName);
                firstFeature.get().getAttributes().put("ID", idValue);

                // Set Parent only for children
                gfFeatures.stream().skip(1).forEach(feature -> {
                    feature.getAttributes().put("Parent", idValue);
                    feature.getAttributes().remove("gene");
                });
            }
        }
    }

private String getStrand(Feature feature) {
    return feature.getLocations().isComplement()
            ? "-"
            : "+";
}


    private String getPhase(Feature feature) {

        // Rule: Use the phase value if present in a qualified.
        // Rule: If phase qualifier is not present, calculate it only for CDS (default
        // 0) or use "." otherwise

        Qualifier phase = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
                .findFirst().orElse(null);
        Qualifier codonStart = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("codon_start"))
                .findFirst().orElse(null);
        if (phase != null) {
            return phase.getValue();
        } else if (feature.getName().equalsIgnoreCase("CDS")) {
            return codonStart == null ? "0" : String.valueOf((Long.parseLong(codonStart.getValue()) - 1));
        }

        return ".";
    }


    private String getPartiality(Feature feature) {

        StringJoiner partiality = new StringJoiner(",");

        if (feature.getLocations().isFivePrimePartial()) {
            partiality.add("start");
        }
        if (feature.getLocations().isThreePrimePartial()) {
            partiality.add("end");
        }
        // Returns empty string if non partial location
        return partiality.length() > 1 ? partiality.toString() : "";
    }

    private boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
        boolean firstQualifierMatches = conversionEntry.getQualifier1() == null;
        boolean secondQualifierMatches = conversionEntry.getQualifier2() == null;

        for (Qualifier qualifier : feature.getQualifiers()) {
            String formatted = "/%s=%s".formatted(qualifier.getName(), qualifier.getValue());
            firstQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier1());
            secondQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier2());
        }
        return firstQualifierMatches && secondQualifierMatches;
    }
}
