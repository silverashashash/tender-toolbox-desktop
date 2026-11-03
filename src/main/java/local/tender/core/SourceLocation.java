package local.tender.core;
import java.nio.file.Path;
public record SourceLocation(Path file,String sheet,Integer row,Integer column) {}
