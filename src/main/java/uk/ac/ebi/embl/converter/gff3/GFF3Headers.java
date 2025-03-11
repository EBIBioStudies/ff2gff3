package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;


public record GFF3Headers(
        String version,
        String accession,
        long start,
        long end
) implements IGFF3Feature {
    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        writer.write("##gff-version %s\n##sequence-region %s %d %d\n"
                .formatted(version, accession, start, end));
    }
}
