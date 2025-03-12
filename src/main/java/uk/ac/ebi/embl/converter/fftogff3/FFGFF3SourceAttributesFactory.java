package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceMetadata;

import java.util.Optional;

public class FFGFF3SourceAttributesFactory implements IConversionRule<Entry, GFF3SourceMetadata> {

    @Override
    public GFF3SourceMetadata from(Entry entry) throws ConversionError {

        Feature feature = Optional.ofNullable(entry.getPrimarySourceFeature())
                .orElseThrow(FFGFF3HeadersFactory.NoSourcePresent::new);
        String organism = feature.getQualifiers("organism").stream().findFirst()
                .map(Qualifier::getValue)
                .orElseGet(() -> null);
        return new GFF3SourceMetadata(organism);

    }
}
