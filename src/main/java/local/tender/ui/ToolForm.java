package local.tender.ui;
import local.tender.core.*;
import local.tender.tools.performance.PerformanceTool;
import java.nio.file.*;
import java.util.*;
public record ToolForm(ToolId id,List<Input> inputs,List<Parameter> parameters,String outputName,String help) {
 public record Input(String key,String label,boolean multiple,List<String> templateHeaders,boolean optional){public Input(String key,String label,boolean multiple,List<String> templateHeaders){this(key,label,multiple,templateHeaders,false);}}
 public record Parameter(String key,String label,String initial,boolean secret){}
 private static Input input(String key,String label,String... headers){return new Input(key,label,false,List.of(headers));}
 public static ToolForm forTool(ToolId id){return switch(id){
  case WARNING_FORM -> new ToolForm(id,List.of(new Input("risk","风险提示表",false,local.tender.tools.warningform.WarningFormTool.RISK_HEADERS),new Input("groups","标包分组表",false,local.tender.tools.warningform.WarningFormTool.GROUP_HEADERS),new Input("bids","开标一览表",false,local.tender.tools.warningform.WarningFormTool.BID_HEADERS)),List.of(new Parameter("date","确认单日期（可空）","",false)),"预警事项确认单.zip",
   "输入｜风险表的注册地址/联系方式/邮箱及是否相同标记、标包技术/商务分组、开标项目信息\n\n匹配｜同标包不同投标人；预警标记组合与对应信息均相同\n\n输出｜同标包同一预警信息的多家企业合并一单，技术/商务组分别输出；企业名单一致才合并包号，打包为ZIP\n\n模板｜使用原版Word模板及固定复核文字，保留专家签字位置");
  case PRICE_SCORE -> new ToolForm(id,List.of(new Input("files","有效报价表",true,local.tender.tools.pricescore.PriceScoreTool.HEADERS),new Input("rules","价格公式参数表",false,local.tender.tools.pricescore.PriceScoreTool.RULE_HEADERS)),List.of(new Parameter("round","计算轮次（可空）","",false)),"价格分计算.xlsx",
   "公式｜先选择模板使用公式再导出；公式名称自动填入，仅保留所需参数列。填写分标名称及参数后导入计算\n\n有效报价｜仅使用进入详评报价；是否参与计算填否可排除，空或是参与\n\n轮次｜多轮次须明确填写；同标包不得混合总价/折扣率或重复供应商\n\n单位｜总价万元；折扣率写95%或0.95；参数为小数，qz为权重分值\n\n参数说明｜详见desktop-toolbox/docs/rules/pricescore.md，导出模板后按采购文件填写；不得直接照用演示参数");
  case AWARD -> new ToolForm(id,List.of(new Input("files","技术经济排序表",true,local.tender.tools.award.AwardTool.INPUT_HEADERS),new Input("groups","限授配置（可空）",false,local.tender.tools.award.AwardTool.GROUP_HEADERS,true)),List.of(new Parameter("mode","方式（无限授/一轮一包/一轮多包）","无限授",false)),"授标结果.xlsx",
   "输入｜原技术经济指标综合评分及排序表，或填写模板；使用原排序列，不重新评分\n\n限授｜每组统一供应商最大中包数；限授模式需填写配置，未纳入组的包按无限授处理\n\n顺序｜当前可授候选报价高优先，再按授标顺序及输入顺序；不同报价口径不可混用\n\n输出｜每包拟授标人及轮次，候选耗尽也保留状态；仅为辅助结果，需按采购规则复核");
  case SPLIT_PACK -> new ToolForm(id,List.of(new Input("materials","物料清单",false,local.tender.tools.splitpack.SplitPackTool.MATERIAL_HEADERS),new Input("rules","分包规则表",false,local.tender.tools.splitpack.SplitPackTool.RULE_HEADERS)),List.of(new Parameter("mode","分配方式（比例/金额）","比例",false),new Parameter("weightStep","重量粒度（千克）","1",false),new Parameter("lengthStep","长度粒度（米）","1",false)),"分包结果数据.xlsx",
   "规则｜分包表每行一个包；分配值按比例模式解释为权重，按金额模式解释为目标金额\n\n数量｜可按粒度拆分；吨/千米自动换算，物料表可填写单独粒度（使用原单位）\n\n分配｜先分基础粒度，再按剩余目标分配，最后轮转尾料；保持数量及原价总金额守恒\n\n结果｜汇总及分包明细，展示目标与实际差额；这是分包建议，不保证硬限额或最优解");
  case CONTACT_WARNING -> new ToolForm(id,List.of(
   input("bidList","开标一览表","分标名称","分包名称","投标人名称"),
   input("contacts","供应商联系人表","投标人名称","投标人联系人姓名","手机号码","邮政地址","固定电话","电子邮件","传真号码")),List.of(),"供应商联系人预警.xlsx",
   "核验内容｜固定电话 · 电子邮件 · 传真 · 地址 · 手机 · 联系人姓名\n\n核验范围｜同一分标、分包内不同供应商\n\n结果说明｜预警是人工核实线索，不等同于违规结论");
  case ECONOMIC_RANK -> new ToolForm(id,List.of(new Input("files","经济排序表",true,List.of())),List.of(),"经济排序核验.xlsx","核验对象｜表内“排序”列；每个工作表对应一个包\n\n排序规则｜总分高优先 → 技术分高优先 → 投标总价低优先 → 商务分高优先\n前项相同才比较下一项；全部相同按原表顺序编号。\n\n结果输出｜仅列排序差异、零分问题；无问题时仅有表头\n\n零分处理｜技术／商务／价格分为0，报告问题并跳过该表排序\n\n核验范围｜使用已有分数与报价；不复算各项得分及总分加总\n\n输入格式｜第4行分标说明 · 第6行表头 · 第7行起数据");
  case IP_MAC -> new ToolForm(id,List.of(new Input("files","IP/MAC对比工作簿",true,local.tender.tools.ipmac.IpMacTool.HEADERS)),List.of(),"IP_MAC核验.xlsx",
   "输入｜投标文件IP地址一览表.xlsx；保留原23列顺序\n\n范围｜同一分标＋分包的不同投标人；包括跨文件类别、跨轮次比较；不同采购批次请分开处理\n\n联合判定｜同一类别记录内的CPU＋硬盘必须同时相同；双方有MAC时，MAC也必须相同；IP不参与判定\n\nMAC缺失｜双方无MAC：按CPU＋硬盘比较；仅一方缺失：待人工核实\n\n资料不足｜CPU或硬盘缺失时列出未完成核验；空白及/不作为相同值\n\n多设备｜整格值比较，暂不拆分多个设备值检查局部重合\n\n输出｜双方投标人、硬件值、文件类别、轮次和来源行；组合一致须结合原始文件复核");
  case EXPERT_OBJECTIVE -> new ToolForm(id,List.of(new Input("files","专家评分明细表",true,List.of()),new Input("rules","客观项规则表",false,local.tender.tools.objective.ObjectiveTool.RULE_HEADERS)),List.of(new Parameter("section","分标名称（可自动提取）","",false)),"专家客观分核验.xlsx",
   "输入｜直接多选解压后的专家评分明细.xlsx；文件名示例：商务评分表_专家编号_专家姓名.xlsx\n\n选择客观项｜先选择评分明细，再点“勾选客观项”；按评分办法分标签页展示实际细则及适用包，各页独立勾选，默认不选；也可使用规则表。步长留空只检查同分，填0.5检查0.5倍数\n\n格式｜第3行评分细则表头，第4行D列起供应商，第5行起评分至合计；兼容含页码的实际导出表，支持负分扣分项\n\n核验｜同供应商同细则各专家分数一致；分值步长正确；缺少专家或未找到细则明确提示\n\n范围｜仅规则表中所选细则，不自动把主观项当客观项；不修改原始评分");
  case LIMIT_PRICE -> new ToolForm(id,List.of(new Input("quotes","行报价表",false,local.tender.tools.limitprice.LimitPriceTool.QUOTE_HEADERS),new Input("limits","限价清单",false,local.tender.tools.limitprice.LimitPriceTool.SERVICE_LIMIT_HEADERS)),List.of(new Parameter("projectType","项目类型（服务/物资）","服务",false),new Parameter("limitBasis","物资限价口径","总价",false),new Parameter("allowedTaxes","允许税率（%）","",false),new Parameter("proofTaxes","证明核查税率（%）","0,1",false)),"报价核验.xlsx",
   "服务类｜只读含税总价或折扣率；不读取单价。两项同时填写时提示冲突\n\n报价识别｜总价有值→总价；折扣率有值→折扣率；均为空才提示缺失\n\n单位｜总价万元对比限价元；折扣率按百分数比较，不做元/万元换算\n\n限价表｜必填报价方式：总价/折扣率；总价限值单位元，折扣限值为比例（1=100%）\n\n一致性｜按报价方式匹配；不以限值1猜测方式；方式冲突提示未核验\n\n匹配｜分标名称＋采购申请标识；同分标内完整编号优先，否则截末5位匹配\n\n税率选择｜填逗号分隔的数字；允许税率留空或填自动时，服务为0,1,3,5,6,9,13，物资为13\n\n提示分类｜不在允许清单→税率异常；命中证明清单→核查税率证明；证明清单填无可关闭，不直接认定纳税人身份\n\n模板｜保存到结果目录；物资模式保留单价/总价逻辑");
  case INITIAL_REPORT -> new ToolForm(id,List.of(input("report","初评工作簿（3张表）")),List.of(new Parameter("department","组别（可自动提取）","",false),new Parameter("clarification","澄清事项（请核实）","",false)),"初评汇报单.docx",
   "输入顺序｜否决明细 · 基本情况 · 组长信息\n\n名称与包号｜可简化企业名称、编辑后缀并保存；同分标、同原因、同企业合并包号，数字排序、去重及连续区间合并\n\n汇总内容｜标段数、包数、投标人数、投标文件份数\n\n否决明细｜按分标、原因、包归并；保留完整供应商名称\n\n澄清事项｜填写实际情况；留空会标注待核实，不自动判断为无\n\n输出｜Word汇报稿；使用前核实组别、署名及汇总数量");
  case PERFORMANCE_SCORE -> new ToolForm(id,List.of(new Input("fileA","开标及部门信息",false,PerformanceTool.BID_HEADERS),new Input("fileB","履约评价表",false,PerformanceTool.SCORE_HEADERS)),List.of(),"履约分.xlsx",
   "关联规则｜单位＋供应商；按关联类型追加分标或所属部门\n\n评价换算｜A/优秀→5 · B/良好→4 · C/一般→3 · D/及格→2 · E/不及格→1\n\n数值边界｜≥90→5 · >80→4 · >60→3 · >30→2 · <30→1\n\n复核提示｜未匹配默认4；空白、/、文字0按原版给4；未知评价及恰好30不覆盖已有有效评价，否则默认4，并提示；多条匹配采用末条有效评价\n\n输出｜原开标信息＋综合评价＋履约分＋匹配说明");
  case EXPERT_TOTAL -> new ToolForm(id,List.of(new Input("scores","专家总分工作簿",true,List.of())),List.of(new Parameter("expertNum","专家人数","5",false),new Parameter("maxScoreDiff","分差阈值","7",false),new Parameter("password","文件口令（可空）","",true)),"专家总分核验.xlsx",
   "检查项目｜超过半数专家同分 · 任意两名专家分差超过阈值\n\n阈值边界｜默认7分；分差等于7不预警，大于7才预警\n\n输入｜原专家总分表；支持多个文件、多个包及分页\n\n输出｜只列问题，显示实际分差、高低分及对应专家姓名；同分预警列出专家\n\n人数须与表内一致；加密文件口令仅本次使用");
  case REJECTION_NOTICE -> new ToolForm(id,List.of(input("excluded","否决明细表","分标名称","投标人","否决模板"),input("sectionOrder","分标顺序表","序号","分标名称")),List.of(),"否决事项公示.docx",
   "归并方式｜按分标、否决原因汇总\n\n数量口径｜投标人数去重；投标文件份数按输入记录行数\n\n顺序｜按分标顺序表排列；未配置的分标放末尾并提示\n\n输出｜Word公示表；请先核对明细是否含重复导入记录");
  default -> throw new IllegalArgumentException("此工具尚未接入表单");
 };}
 public static final class State {
  private final ToolForm form;private final Map<String,List<Path>> inputs=new LinkedHashMap<>();private final Map<String,String> parameters=new LinkedHashMap<>();private Path output;
  public State(ToolForm form,Path output){this.form=form;this.output=output;for(var p:form.parameters())parameters.put(p.key(),p.initial());}
  public void select(String key,List<Path> paths){
   var spec=form.inputs().stream().filter(i->i.key().equals(key)).findFirst().orElseThrow();if(paths.isEmpty()&&spec.optional()){inputs.remove(key);return;}if(paths.isEmpty()||(!spec.multiple()&&paths.size()!=1))throw new IllegalArgumentException("请选择文件："+spec.label());
   inputs.put(key,List.copyOf(paths));output=paths.get(paths.size()-1).toAbsolutePath().getParent();
  }
  public void clearInput(String key){inputs.remove(key);}
  public List<Path> paths(String key){return inputs.getOrDefault(key,List.of());}
  public Path output(){return output;}public void output(Path path){output=path;}
  public String parameter(String key){return parameters.getOrDefault(key,"");}public void parameter(String key,String value){parameters.put(key,value);}
  public ToolRequest request(boolean overwrite){
   for(var input:form.inputs())if(!input.optional()&&paths(input.key()).isEmpty())throw new IllegalArgumentException("请选择："+input.label());
   var params=new LinkedHashMap<>(parameters);params.put("overwrite",Boolean.toString(overwrite));return new ToolRequest(form.id(),inputs,params,output,Optional.empty());
  }
 }
}
