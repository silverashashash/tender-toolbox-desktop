package local.tender.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Shared history for approved file/directory selections, independent of tool. */
public final class ChooserHistory {
 private static final String KEY="chooser.lastDirectory";
 private final Path config,fallback;
 private Path recent;
 public ChooserHistory(Path config,Path fallback){
  this.config=config;this.fallback=fallback.toAbsolutePath().normalize();
  try {String value=load().getProperty(KEY);if(value!=null)recent=Path.of(value);}catch(IOException|IllegalArgumentException ignored){/* Recover with fallback; never overwrite on read. */}
 }
 public Path directory(){
  Path existing=existingParent(recent);return existing!=null?existing:existingParent(fallback);
 }
 private static Path existingParent(Path path){
  while(path!=null&&!Files.isDirectory(path))path=path.getParent();return path;
 }
 private Properties load()throws IOException {
  var properties=new Properties();if(Files.exists(config))try(var in=Files.newInputStream(config)){properties.load(in);}return properties;
 }
 public void remember(Path selection,boolean directory)throws IOException {
  Path absolute=selection.toAbsolutePath().normalize();recent=directory?absolute:absolute.getParent();
  var properties=load();String value=recent.toString();if(value.equals(properties.getProperty(KEY)))return;
  properties.setProperty(KEY,value);Files.createDirectories(config.toAbsolutePath().getParent());
  Path staged=Files.createTempFile(config.toAbsolutePath().getParent(),"chooser-",".tmp");
  try {
   try(var out=Files.newOutputStream(staged)){properties.store(out,"TenderToolbox local preferences");}
   if(Files.exists(config))Files.copy(config,config.resolveSibling(config.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
   try{Files.move(staged,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){Files.move(staged,config,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(staged);}
 }
}
