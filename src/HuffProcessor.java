import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
		
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] freqs = new int[ALPH_SIZE + 1];
		//if (myDebugLevel >= DEBUG_HIGH) {
			System.out.println("chunk freq");
		//}
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			else {
				freqs[bits] = 1 + freqs[bits];
				//if (myDebugLevel >= DEBUG_HIGH) {
					System.out.print(bits + freqs[bits]);
				//}
			}
		}
		freqs[PSEUDO_EOF] = 1;
		return freqs; 
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		System.out.println("fired");
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int i=0; i< counts.length; i++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i,counts[i],null,null));
			}
		}
		//if (myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq create with %d nodes\n", pq.size());
		//}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(left.myValue + right.myValue,right.myWeight + left.myWeight, left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "",encodings);
		return encodings;
		}
	
	private void codingHelper(HuffNode n, String path, String[] encode) {
		if (n == null) {
			return;
		}
		else if (n.myLeft == null && n.myRight == null) {
			encode[n.myValue] = path;
			//if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", n.myValue,path);
			//}
			return;
		}
		else {
			codingHelper(n.myLeft,path+"0",encode);
			codingHelper(n.myRight,path+"1",encode);
		}
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		HuffNode current = root;
		if (current.myRight == null && current.myLeft == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, current.myValue);
			//if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("wrote leaf for tree %d", current.myValue);
			//}
		}
		else {
			out.writeBits(1,0);
			current = current.myLeft;
			current = current.myRight;
		}
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			else if (!codings[bits].equals("")){
				String code = codings[bits];
				out.writeBits(code.length(), Integer.parseInt(code,2));
			}
		}
		//String code1 = codings[PSEUDO_EOF];
		//out.writeBits(code1.length(), Integer.parseInt(code1,2));
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		if(bits == -1) {
			throw new HuffException("reading bits failed"+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	private HuffNode readTreeHeader(BitInputStream in1) {
		
		while (true) {
			int bit = in1.readBits(1);
			if (bit == -1) {
				throw new HuffException("bad input");
			}
			if (bit == 0) {
				HuffNode left = readTreeHeader(in1);
				HuffNode right = readTreeHeader(in1);
				return new HuffNode(0,0,left,right);
			}
			else {
				int value = in1.readBits(BITS_PER_WORD + 1);
				return new HuffNode(value,0,null,null);
			}
		}
	}
		
	private void readCompressedBits(HuffNode n, BitInputStream in2, BitOutputStream out) {
		HuffNode current = n;
		while (true) {
			int bits = in2.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null ) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = n;
					}
				}
			}
		}
	}
	public static void main(String args[]) {
		HuffProcessor hp = new HuffProcessor(4);
	}
}