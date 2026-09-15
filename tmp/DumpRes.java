import haven.Message;
import haven.Resource;
import haven.StreamMessage;
import java.net.URI;

public class DumpRes {
    public static void main(String[] args) throws Exception {
	Resource.HttpSource src = new Resource.HttpSource(URI.create("https://game.havenandhearth.com/res/"));
	for(String name : args) {
	    try(java.io.InputStream in = src.get(name)) {
		Message fp = new StreamMessage(in);
		byte[] sig = fp.bytes("Haven Resource 1".length());
		int ver = fp.uint16();
		System.out.println("=== " + name + " v" + ver);
		while(!fp.eom()) {
		    String lay = fp.string();
		    int len = fp.int32();
		    Message buf = new haven.LimitMessage(fp, len);
		    String extra = "";
		    if(lay.equals("codeentry") || lay.equals("slink") || lay.equals("rlink") || lay.equals("props") || lay.equals("code")) {
			byte[] b = buf.bytes();
			extra = " " + new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).replace('\0', '|');
		    } else {
			buf.skip();
		    }
		    System.out.println("  " + lay + " " + len + extra);
		}
	    } catch(Exception e) {
		System.out.println("=== " + name + " FAIL " + e);
	    }
	}
    }
}
