package local.tender.core;
import java.nio.file.Path;import java.util.List;
public record ToolResult(List<Path> outputs,List<Problem> warnings){public ToolResult{outputs=List.copyOf(outputs);warnings=List.copyOf(warnings);}}
