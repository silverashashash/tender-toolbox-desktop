package local.tender.io;
import local.tender.core.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;
public final class SafeZip {
 private SafeZip(){}
 public static Outcome<List<Path>> extract(Path archive,Path ownedDirectory,long maxExpandedBytes,TaskContext context){
  boolean created=false;Path root=ownedDirectory.toAbsolutePath().normalize();
  try {
   if(maxExpandedBytes<0)return Outcome.fail("ZIP_BUDGET","解压容量无效");
   context.checkCancelled();Files.createDirectory(root);created=true;
   var paths=new ArrayList<Path>();long total=0;
   try(var zip=new ZipFile(archive.toFile())){
    var entries=zip.entries();var seen=new HashSet<Path>();byte[] buffer=new byte[32768];
    while(entries.hasMoreElements()){
     context.checkCancelled();var entry=entries.nextElement();String name=entry.getName();
     if(name.indexOf('\\')>=0||name.startsWith("/")||name.matches("^[A-Za-z]:.*"))throw new IOException("不支持的ZIP路径："+name);
     Path target=root.resolve(name).normalize();
     if(!target.startsWith(root)||target.equals(root)||!seen.add(target))throw new IOException("ZIP路径越界或重复："+name);
     if(entry.isDirectory()){Files.createDirectories(target);continue;}
     if(entry.getSize()>maxExpandedBytes-total)throw new IOException("展开内容超过允许容量");
     Files.createDirectories(target.getParent());
     try(var in=zip.getInputStream(entry);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
      int count;while((count=in.read(buffer))!=-1){context.checkCancelled();if(count>maxExpandedBytes-total)throw new IOException("展开内容超过允许容量");out.write(buffer,0,count);total+=count;}
     }paths.add(target);
    }
   }
   return Outcome.ok(List.copyOf(paths));
  }catch(IOException|CancellationException|InvalidPathException e){
   String cleanup="";if(created)try{deleteOwnedTree(root);}catch(IOException failure){cleanup="；临时目录未清理："+root+"："+failure.getMessage();}
   return Outcome.fail(e instanceof CancellationException?"CANCELLED":"ZIP_INVALID",String.valueOf(e.getMessage())+cleanup);
  }
 }
 static void deleteOwnedTree(Path root)throws IOException{
  try(var stream=Files.walk(root)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
 }
}
