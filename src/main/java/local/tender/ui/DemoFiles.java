package local.tender.ui;
import local.tender.io.WorkbookTable;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
/** Synthetic data for checking a new machine; never contains supplier records. */
public record DemoFiles(Path bids,Path contacts,Path output) {
 public static DemoFiles create(Path parent) throws IOException {
  Files.createDirectories(parent);
  Path folder=Files.createTempDirectory(parent,"联系人预警演示-");
  Path bids=folder.resolve("演示开标表.xlsx"),contacts=folder.resolve("演示联系人表.xlsx");
  WorkbookTable.write(bids,"开标一览表",List.of("分标名称","分包名称","投标人名称"),List.of(
   List.of("验证分标","包01","示例甲公司"),List.of("验证分标","包01","示例乙公司")));
  WorkbookTable.write(contacts,"联系人",List.of("投标人名称","投标人联系人姓名","手机号码","邮政地址","固定电话","电子邮件","传真号码"),List.of(
   List.of("示例甲公司","示例联系人甲","111","甲市","001","a@example.test","012345"),
   List.of("示例乙公司","示例联系人乙","222","乙市","002","b@example.test","012345")));
  Path output=Files.createDirectory(folder.resolve("结果"));
  return new DemoFiles(bids,contacts,output);
 }
}
