package local.tender.core;
public interface ToolHandler {ToolId id();Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception;}
