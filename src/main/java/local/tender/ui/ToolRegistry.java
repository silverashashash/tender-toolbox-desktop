package local.tender.ui;
import local.tender.core.*;import java.util.*;
public final class ToolRegistry {
 public record Entry(int number,ToolId id,String name) {public String toString(){return number+"  "+name;}}
 private final Map<ToolId,ToolHandler> handlers=new EnumMap<>(ToolId.class);
 private static final List<Entry> CATALOG=List.of(
  new Entry(1,ToolId.WARNING_FORM,"预警事项确认单"),new Entry(2,ToolId.CONTACT_WARNING,"供应商联系人预警"),new Entry(3,ToolId.IP_MAC,"IP/MAC 校验"),new Entry(4,ToolId.LIMIT_PRICE,"超限价及税率核验"),new Entry(5,ToolId.PRICE_SCORE,"价格分自动计算"),new Entry(6,ToolId.INITIAL_REPORT,"初评汇报"),new Entry(7,ToolId.PERFORMANCE_SCORE,"履约分生成"),new Entry(8,ToolId.EXPERT_TOTAL,"专家总分校验"),new Entry(9,ToolId.EXPERT_OBJECTIVE,"专家客观分校验"),new Entry(10,ToolId.AWARD,"授标工具"),new Entry(11,ToolId.ECONOMIC_RANK,"经济排序表校验"),new Entry(12,ToolId.REJECTION_NOTICE,"否决原因公示"),new Entry(14,ToolId.SPLIT_PACK,"智能分包"));
 public static ToolRegistry standard(){return new ToolRegistry(List.of(new local.tender.tools.contactwarning.ContactWarningTool(),new local.tender.tools.economicrank.EconomicRankTool(),new local.tender.tools.performance.PerformanceTool(),new local.tender.tools.experttotal.ExpertTotalTool(),new local.tender.tools.rejection.RejectionTool(),new local.tender.tools.limitprice.LimitPriceTool(),new local.tender.tools.initialreport.InitialReportTool(),new local.tender.tools.ipmac.IpMacTool(),new local.tender.tools.objective.ObjectiveTool(),new local.tender.tools.warningform.WarningFormTool(),new local.tender.tools.pricescore.PriceScoreTool(),new local.tender.tools.award.AwardTool(),new local.tender.tools.splitpack.SplitPackTool()));}
 public ToolRegistry(List<ToolHandler> handlers){for(var handler:handlers)if(this.handlers.putIfAbsent(handler.id(),handler)!=null)throw new IllegalArgumentException("重复工具 ID："+handler.id());}
 public List<Entry> catalog(){return CATALOG;}
 public Outcome<ToolHandler> find(ToolId id){var h=handlers.get(id);return h==null?Outcome.fail("NOT_IMPLEMENTED","此功能正在迁移，尚未开放运行"):Outcome.ok(h);}
}
