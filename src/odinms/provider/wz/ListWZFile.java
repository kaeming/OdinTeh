package odinms.provider.wz;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import odinms.provider.MapleDataProviderFactory;
import odinms.tools.data.input.GenericLittleEndianAccessor;
import odinms.tools.data.input.InputStreamByteStream;
import odinms.tools.data.input.LittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListWZFile {
	private LittleEndianAccessor lea;
	private List<String> entries = new ArrayList<String>();
	private static Collection<String> modernImgs = new HashSet<String>();
	
	private static Logger log = LoggerFactory.getLogger(ListWZFile.class);

	public static byte[] xorBytes(byte[] a, byte[] b) {
		byte[] wusched = new byte[a.length];
		for (int i = 0; i < a.length; i++) {
			wusched[i] = (byte) (a[i] ^ b[i]);
		}
		return wusched;
	}

	public ListWZFile(File listwz) throws FileNotFoundException {
		lea = new GenericLittleEndianAccessor(new InputStreamByteStream(new BufferedInputStream(new FileInputStream(listwz))));

		while (lea.available() > 0) {
			int l = lea.readInt() * 2;
			byte[] chunk = new byte[l];
			for (int i = 0; i < chunk.length; i++) {
				chunk[i] = lea.readByte();
			}
			lea.readChar();
			
			final String value = String.valueOf(WZTool.readListString(chunk));
			entries.add(value);
		}
		entries = Collections.unmodifiableList(entries);
	}
	
	public List<String> getEntries() {
		return entries;
	}
	
	public static void init() {
		final String listWz = System.getProperty("odinms.listwz");
		if (listWz != null) {
			ListWZFile listwz;
			try {
				listwz = new ListWZFile(MapleDataProviderFactory.fileInWZPath("List.wz"));
				modernImgs = new HashSet<String>(listwz.getEntries());
			} catch (FileNotFoundException e) {
				log.info("odinms.listwz is set but the List.wz could not be found", e);
			}
		}
	}
	
	public static boolean isModernImgFile(String path) {
		return modernImgs.contains(path);
	}
}
