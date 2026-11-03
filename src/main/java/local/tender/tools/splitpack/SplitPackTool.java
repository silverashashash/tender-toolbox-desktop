package local.tender.tools.splitpack;
import local.tender.core.*;
import local.tender.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.nio.file.*;
import java.math.*;
import java.util.*;

/** Offline, quantity-splitting reconstruction of the original three-pass allocation. */
public final class SplitPackTool implements ToolHandler {
 public static final List<String> MATERIAL_HEADERS=List.of("序号","物料编码","物料描述","物料长描述","计量单位名称","技术规范书ID","扩展描述","数量","单价限价","分配粒度（可空）");
 public static final List<String> RULE_HEADERS=List.of("包号","分配值");
 private static final BigDecimal THOUSAND=new BigDecimal("1000");
 private record Key(String code,String extension){}
 private record Material(Key key,String description,String longDescription,String unit,String specification,BigDecimal quantity,BigDecimal price,BigDecimal factor,BigDecimal step){
  BigDecimal normalizedQuantity(){return quantity.multiply(factor);}
  BigDecimal normalizedPrice(){return price.divide(factor);}
 }
 private record Pack(String number,BigDecimal value,BigDecimal target){}
 public ToolId id(){return ToolId.SPLIT_PACK;}
 public Outcome<ToolResult> execute(ToolRequest request,TaskContext context)throws Exception{
  try{
   String mode=request.parameters().getOrDefault("mode","比例");if(!Set.of("比例","金额").contains(mode))throw new IOException("分配方式必须为比例或金额");
   BigDecimal weight=positive(request.parameters().getOrDefault("weightStep","1"),"重量粒度（千克）"),length=positive(request.parameters().getOrDefault("lengthStep","1"),"长度粒度（米）");
   var materials=new ArrayList<Material>();var keys=new HashSet<Key>();BigDecimal total=BigDecimal.ZERO;
   var required=new LinkedHashMap<String,List<String>>();for(String h:MATERIAL_HEADERS.subList(0,9))required.put(h,List.of(h));
   var tables=WorkbookTable.readAll(single(request,"materials"),required,Map.of("分配粒度（可空）",List.of("分配粒度（可空）","分配粒度")));
   for(var table:tables.entrySet())for(var row:table.getValue().rows()){
    context.checkCancelled();String location=table.getKey()+" 第"+row.get("__row")+"行";
    var key=new Key(nonblank(row.get("物料编码"),location+"物料编码"),row.get("扩展描述"));if(!keys.add(key))throw new IOException(location+" 物料编码＋扩展描述重复，请先合并数量");
    String unit=nonblank(row.get("计量单位名称"),location+"计量单位名称");BigDecimal factor=Set.of("吨","千米").contains(unit)?THOUSAND:BigDecimal.ONE;
    BigDecimal quantity=positive(WorkbookTable.numericText(row,"数量"),location+"数量"),price=positive(WorkbookTable.numericText(row,"单价限价"),location+"单价限价");
    String override=Objects.requireNonNullElse(WorkbookTable.numericText(row,"分配粒度（可空）"),"");BigDecimal step=override.isBlank()?(Set.of("吨","千克").contains(unit)?weight:Set.of("米","千米").contains(unit)?length:BigDecimal.ONE):positive(override,location+"分配粒度").multiply(factor);
    materials.add(new Material(key,row.get("物料描述"),row.get("物料长描述"),unit,row.get("技术规范书ID"),quantity,price,factor,step));total=total.add(quantity.multiply(price));
   }
   if(materials.isEmpty())throw new IOException("物料表没有数据");
   var packs=new ArrayList<Pack>();var packKeys=new HashSet<String>();BigDecimal valueTotal=BigDecimal.ZERO;
   for(var table:WorkbookTable.readAll(single(request,"rules"),Map.of("包号",List.of("包号"),"分配值",List.of("分配值"))).entrySet())for(var row:table.getValue().rows()){
    String number=nonblank(row.get("包号"),"包号");if(!packKeys.add(number))throw new IOException("包号重复："+number);
    BigDecimal value=positive(WorkbookTable.numericText(row,"分配值"),"包"+number+"分配值");packs.add(new Pack(number,value,BigDecimal.ZERO));valueTotal=valueTotal.add(value);
   }
   if(packs.isEmpty())throw new IOException("分包规则表没有数据");
   if(mode.equals("金额")&&valueTotal.compareTo(total)>0)throw new IOException("各包目标金额合计超过物料总金额");
   for(int i=0;i<packs.size();i++){var p=packs.get(i);BigDecimal target=mode.equals("比例")?total.divide(valueTotal,4,RoundingMode.DOWN).multiply(p.value):p.value;packs.set(i,new Pack(p.number,p.value,target));}
   packs.sort(Comparator.comparing(Pack::target).reversed().thenComparing(Pack::number));
   var quantities=new LinkedHashMap<String,Map<Key,BigDecimal>>();var remaining=new LinkedHashMap<Key,BigDecimal>();var budget=new LinkedHashMap<String,BigDecimal>();
   for(var m:materials)remaining.put(m.key,m.normalizedQuantity());for(var p:packs){quantities.put(p.number,new LinkedHashMap<>());budget.put(p.number,p.target);}
   BigDecimal remainingTarget=packs.stream().map(Pack::target).reduce(BigDecimal.ZERO,BigDecimal::add);
   // Pass 1: one allocation unit per material and package, even when target is exceeded.
   for(var p:packs)for(var m:materials){context.checkCancelled();BigDecimal allocated=remaining.get(m.key).min(m.step);add(quantities.get(p.number),m.key,allocated);remaining.put(m.key,remaining.get(m.key).subtract(allocated));BigDecimal cost=allocated.multiply(m.normalizedPrice());budget.put(p.number,budget.get(p.number).subtract(cost).max(BigDecimal.ZERO));remainingTarget=remainingTarget.subtract(cost);}
   // Pass 2: frozen remaining stock multiplied by each package's remaining-target share.
   var snapshot=new LinkedHashMap<>(remaining);
   if(remainingTarget.signum()>0)for(var p:packs){BigDecimal ratio=budget.get(p.number).divide(remainingTarget,4,RoundingMode.HALF_DOWN);for(var m:materials){context.checkCancelled();BigDecimal amount=snapshot.get(m.key).multiply(ratio).setScale(0,RoundingMode.HALF_DOWN).divide(m.step,0,RoundingMode.HALF_DOWN).multiply(m.step).min(remaining.get(m.key));add(quantities.get(p.number),m.key,amount);remaining.put(m.key,remaining.get(m.key).subtract(amount));}}
   // Pass 3: expensive stock first; aggregate full round-robin cycles without allocating a list of chunks.
   var tail=new ArrayList<>(materials);tail.sort(Comparator.comparing(Material::normalizedPrice).reversed());int cursor=0;BigInteger count=BigInteger.valueOf(packs.size());
   for(var m:tail){context.checkCancelled();BigDecimal stock=remaining.get(m.key);BigDecimal[] chunks=stock.divideAndRemainder(m.step);BigInteger[] rounds=chunks[0].toBigIntegerExact().divideAndRemainder(count);
    if(rounds[0].signum()>0){BigDecimal each=m.step.multiply(new BigDecimal(rounds[0]));for(var p:packs)add(quantities.get(p.number),m.key,each);}
    for(int i=0;i<rounds[1].intValueExact();i++){add(quantities.get(packs.get(cursor).number),m.key,m.step);cursor=(cursor+1)%packs.size();}
    if(chunks[1].signum()>0){add(quantities.get(packs.get(cursor).number),m.key,chunks[1]);cursor=(cursor+1)%packs.size();}remaining.put(m.key,BigDecimal.ZERO);
   }
   for(var m:materials){BigDecimal sum=packs.stream().map(p->quantities.get(p.number).getOrDefault(m.key,BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);if(sum.compareTo(m.normalizedQuantity())!=0)throw new IllegalStateException("分配数量不守恒");}
   var details=new ArrayList<List<String>>();var summary=new ArrayList<List<String>>();BigDecimal grandMoney=BigDecimal.ZERO,grandQuantity=BigDecimal.ZERO,grandTarget=BigDecimal.ZERO;int grandItems=0;
   var outputPacks=new ArrayList<>(packs);outputPacks.sort(Comparator.comparing(Pack::number));
   for(var p:outputPacks){BigDecimal amount=BigDecimal.ZERO,quantity=BigDecimal.ZERO;int items=0;for(var m:materials){BigDecimal normalized=quantities.get(p.number).getOrDefault(m.key,BigDecimal.ZERO);if(normalized.signum()==0)continue;BigDecimal q=normalized.divide(m.factor),money=q.multiply(m.price);items++;amount=amount.add(money);quantity=quantity.add(q);details.add(List.of(String.valueOf(details.size()+1),p.number,m.key.code,m.description,m.longDescription,m.specification,m.key.extension,fmt(q),m.unit,fmt(m.price),fmt(money)));}summary.add(List.of(p.number,String.valueOf(items),fmt(quantity),fmt(amount),fmt(p.target),fmt(amount.subtract(p.target))));grandMoney=grandMoney.add(amount);grandQuantity=grandQuantity.add(quantity);grandTarget=grandTarget.add(p.target);grandItems+=items;}
   if(grandMoney.compareTo(total)!=0)throw new IllegalStateException("分配金额不守恒");summary.add(List.of("合计",String.valueOf(grandItems),fmt(grandQuantity),fmt(grandMoney),fmt(grandTarget),fmt(grandMoney.subtract(grandTarget))));
   var warnings=List.of(new Problem("ALLOCATION_SUGGESTION","分包结果为建议；目标金额不是硬限额。请核对实际金额与目标偏差；不同计量单位的数量合计仅作汇总。",null));
   return ArtifactOutput.publish(request,context,"分包结果数据.xlsx",warnings,path->{try(var wb=new XSSFWorkbook()){sheet(wb,"汇总",List.of("包号","物料数量","总数量","总预算","目标金额","实际减目标"),summary);sheet(wb,"分包结果数据",List.of("序号","包号","物料编码","物料描述","物料长描述","技术规范书ID","扩展描述","数量","计量单位名称","预算单价","预算总价"),details);try(var out=Files.newOutputStream(path)){wb.write(out);}}});
  }catch(IOException|IllegalArgumentException e){return Outcome.fail("INPUT_INVALID",e.getMessage());}
 }
 private static void add(Map<Key,BigDecimal> map,Key key,BigDecimal quantity){if(quantity.signum()>0)map.merge(key,quantity,BigDecimal::add);}
 private static Path single(ToolRequest request,String key)throws IOException{var paths=request.inputs().get(key);if(paths==null||paths.size()!=1)throw new IOException("请选择一个"+(key.equals("materials")?"物料表":"分包规则表"));return paths.get(0);}
 private static String nonblank(String s,String label)throws IOException{if(s==null||s.isBlank())throw new IOException(label+"不能为空");return s;}
 private static BigDecimal positive(String text,String label)throws IOException{try{BigDecimal n=new BigDecimal(text.trim());if(n.signum()<=0)throw new NumberFormatException();return n;}catch(RuntimeException e){throw new IOException(label+"必须为有效正数："+text);}}
 private static String fmt(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
 private static void sheet(XSSFWorkbook wb,String name,List<String> headers,List<List<String>> rows){var s=wb.createSheet(name);var font=wb.createFont();font.setBold(true);var style=wb.createCellStyle();style.setFont(font);var header=s.createRow(0);for(int c=0;c<headers.size();c++){var cell=header.createCell(c);cell.setCellValue(headers.get(c));cell.setCellStyle(style);s.setColumnWidth(c,22*256);}for(int r=0;r<rows.size();r++){var row=s.createRow(r+1);for(int c=0;c<rows.get(r).size();c++)row.createCell(c).setCellValue(rows.get(r).get(c));}s.createFreezePane(0,1);}
}
