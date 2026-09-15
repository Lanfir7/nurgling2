import haven.Message;
import haven.Resource;
import haven.StreamMessage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class DumpSrc {
    public static void main(String[] args) throws Exception {
	Resource.HttpSource src = new Resource.HttpSource(URI.create("https://game.havenandhearth.com/res/"));
	Path outdir = Path.of(args[0]);
	for(int i = 1; i < args.length; i++) {
	    String name = args[i];
	    try(java.io.InputStream in = src.get(name)) {
		Message fp = new StreamMessage(in);
		fp.bytes("Haven Resource 1".length());
		int ver = fp.uint16();
		System.out.println("=== " + name + " v" + ver);
		boolean found = false;
		while(!fp.eom()) {
		    String lay = fp.string();
		    int len = fp.int32();
		    Message buf = new haven.LimitMessage(fp, len);
		    if(lay.equals("src")) {
			int fver = buf.uint8();
			String nm = buf.string();
			byte[] code = buf.bytes();
			Path out = outdir.resolve(name.replace('/', '_') + "_" + nm);
			Files.createDirectories(out.getParent());
			Files.write(out, code);
			System.out.println("  wrote " + out + " (" + code.length + " bytes, srcver=" + fver + ")");
			found = true;
		    } else if(lay.equals("codeentry")) {
			byte[] b = buf.bytes();
			System.out.println("  codeentry " + new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).replace('\0', '|'));
		    } else {
			buf.skip();
		    }
		}
		if(!found)
		    System.out.println("  (no src layer)");
	    } catch(Exception e) {
		System.out.println("=== " + name + " FAIL " + e);
	    }
	}
    }
}
