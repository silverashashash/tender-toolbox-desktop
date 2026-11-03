package local.tender.core;
import java.nio.file.Path;import java.util.*;import java.util.stream.Collectors;
public record ToolRequest(ToolId toolId,Map<String,List<Path>> inputs,Map<String,String> parameters,Path outputDirectory,Optional<Path> projectDirectory){
 public ToolRequest{inputs=inputs.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,e->List.copyOf(e.getValue())));parameters=Map.copyOf(parameters);Objects.requireNonNull(toolId);Objects.requireNonNull(outputDirectory);Objects.requireNonNull(projectDirectory);}
}
