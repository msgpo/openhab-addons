package org.openhab.binding.maxcul.internal.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseMsg {
	public byte len;
	public byte msgCount;
	public byte msgFlag;
	public byte msgTypeRaw;
	public MaxCulMsgType msgType;
	public byte[] srcAddr = new byte[3];
	public String srcAddrStr;
	public byte[] dstAddr = new byte[3];
	public String dstAddrStr;
	public byte groupid;
	public byte[] payload;
	public String rawMsg;

	private boolean flgReadyToSend = false;

	private static final Logger logger =
			LoggerFactory.getLogger(BaseMsg.class);

	/* define number of characters per byte */
	private final static int PKT_CHARS_PER_BYTE = 2;

	/* packet structure in terms of character representations of bytes */
	private final static int PKT_POS_MSG_LEN = 1; /* Account for 'Z' at start */
	private final static int PKT_POS_MSG_LEN_LEN = PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_MSG_START = PKT_POS_MSG_LEN+PKT_POS_MSG_LEN_LEN;

	private final static int PKT_POS_MSG_COUNT = PKT_POS_MSG_LEN+PKT_POS_MSG_LEN_LEN;
	private final static int PKT_POS_MSG_COUNT_LEN = PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_MSG_FLAG = PKT_POS_MSG_COUNT+PKT_POS_MSG_COUNT_LEN;
	private final static int PKT_POS_MSG_FLAG_LEN = PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_MSG_TYPE  = PKT_POS_MSG_FLAG+PKT_POS_MSG_FLAG_LEN;
	private final static int PKT_POS_MSG_TYPE_LEN = PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_SRC_ADDR  = PKT_POS_MSG_TYPE+PKT_POS_MSG_TYPE_LEN;
	private final static int PKT_POS_SRC_ADDR_LEN = 3*PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_DST_ADDR  = PKT_POS_SRC_ADDR+PKT_POS_SRC_ADDR_LEN;
	private final static int PKT_POS_DST_ADDR_LEN = 3*PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_GROUP_ID  = PKT_POS_DST_ADDR+PKT_POS_DST_ADDR_LEN;
	private final static int PKT_POS_GROUP_ID_LEN = PKT_CHARS_PER_BYTE;

	private final static int PKT_POS_PAYLOAD_START = PKT_POS_GROUP_ID + PKT_POS_GROUP_ID_LEN;


	/**
	 * Constructor based on received message
	 * @param rawMsg
	 */
	public BaseMsg(String rawMsg)
	{
		BaseMsg pkt = this;

		this.rawMsg = rawMsg;

		pkt.len = (byte) (Integer.parseInt(rawMsg.substring(1, 3),16) & 0xFF); /* length of packet */
		if (pkt.len != ((rawMsg.length()-5)/2)) /* -5 => 'Z' and len byte (2 chars) and checksum at end?, div by two as it is a hex string  */
		{
			logger.error("Unable to process packet "+rawMsg+". Length is not correct.");
			pkt.len = 0; /* indicate not a valid packet */
			return;
		}

		pkt.msgCount = (byte) (Integer.parseInt(rawMsg.substring(PKT_POS_MSG_COUNT,PKT_POS_MSG_COUNT+PKT_POS_MSG_COUNT_LEN),16) & 0xFF);

		pkt.msgFlag = (byte) (Integer.parseInt(rawMsg.substring(PKT_POS_MSG_FLAG,PKT_POS_MSG_FLAG+PKT_POS_MSG_FLAG_LEN),16) & 0xFF);

		pkt.msgTypeRaw = (byte) (Integer.parseInt(rawMsg.substring(PKT_POS_MSG_TYPE,PKT_POS_MSG_TYPE+PKT_POS_MSG_TYPE_LEN),16) & 0xFF);
		pkt.msgType = MaxCulMsgType.fromByte(pkt.msgTypeRaw);

		pkt.srcAddrStr = rawMsg.substring(PKT_POS_SRC_ADDR,PKT_POS_SRC_ADDR+PKT_POS_SRC_ADDR_LEN);
		for (int idx = PKT_POS_SRC_ADDR; idx < PKT_POS_SRC_ADDR+PKT_POS_SRC_ADDR_LEN; idx += PKT_CHARS_PER_BYTE)
		{
			pkt.srcAddr[(idx-PKT_POS_SRC_ADDR)/PKT_CHARS_PER_BYTE] = (byte) (Integer.parseInt(rawMsg.substring(idx,idx+PKT_CHARS_PER_BYTE),16) & 0xFF);
		}

		pkt.dstAddrStr = rawMsg.substring(PKT_POS_DST_ADDR,PKT_POS_DST_ADDR+PKT_POS_DST_ADDR_LEN);
		for (int idx = PKT_POS_DST_ADDR; idx < PKT_POS_DST_ADDR+PKT_POS_DST_ADDR_LEN; idx += PKT_CHARS_PER_BYTE)
		{
			pkt.dstAddr[(idx-PKT_POS_DST_ADDR)/PKT_CHARS_PER_BYTE] = (byte) (Integer.parseInt(rawMsg.substring(idx,idx+PKT_CHARS_PER_BYTE),16) & 0xFF);
		}

		pkt.groupid = (byte) (Integer.parseInt(rawMsg.substring(PKT_POS_GROUP_ID,PKT_POS_GROUP_ID+PKT_POS_GROUP_ID_LEN),16) & 0xFF);

		/* pkt.len accounts for message only (i.e. not first 3 chars) - so offset for characters that precede the message */
		int payloadStrLen = ((pkt.len)*PKT_CHARS_PER_BYTE)+PKT_POS_MSG_START-PKT_POS_PAYLOAD_START;
		int payloadByteLen = payloadStrLen / PKT_CHARS_PER_BYTE;
		logger.debug("Payload length = "+payloadStrLen+" => "+(payloadByteLen));
		pkt.payload = new byte[payloadByteLen];
		for (int payIdx = PKT_POS_PAYLOAD_START; payIdx < (PKT_POS_PAYLOAD_START+payloadStrLen); payIdx+=PKT_CHARS_PER_BYTE )
		{
			pkt.payload[(payIdx-PKT_POS_PAYLOAD_START)/PKT_CHARS_PER_BYTE] = (byte) (Integer.parseInt(rawMsg.substring(payIdx,payIdx+PKT_CHARS_PER_BYTE),16) & 0xFF);
		}
	}

	/**
	 * This constructor creates an incomplete raw message ready for sending. Payload must be set before sending.
	 * @param msgCount Message Counter
	 * @param msgFlag Message flag
	 * @param msgType the message type
	 * @param groupId Group ID
	 * @param srcAddr Source address of controller
	 * @param dstAddr Dest addr of device
	 */
	public BaseMsg(byte msgCount, byte msgFlag, MaxCulMsgType msgType, byte groupId, String srcAddr, String dstAddr)
	{
		StringBuilder sb = new StringBuilder();

		this.msgCount = msgCount;
		sb.append(String.format("%02x", msgCount).toUpperCase());

		this.msgFlag = msgFlag;
		sb.append(String.format("%02x", msgFlag).toUpperCase());

		this.msgType = msgType;
		this.msgTypeRaw = this.msgType.toByte();
		sb.append(String.format("%02x", this.msgTypeRaw).toUpperCase());

		this.srcAddrStr = srcAddr;
		this.srcAddr[0] = (byte) (Integer.parseInt(srcAddr.substring(0,2),16) & 0xFF);
		this.srcAddr[1] = (byte) (Integer.parseInt(srcAddr.substring(2,4),16) & 0xFF);
		this.srcAddr[2] = (byte) (Integer.parseInt(srcAddr.substring(4,6),16) & 0xFF);
		sb.append(srcAddr.toUpperCase());

		this.dstAddrStr = dstAddr;
		this.dstAddr[0] = (byte) (Integer.parseInt(dstAddr.substring(0,2),16) & 0xFF);
		this.dstAddr[1] = (byte) (Integer.parseInt(dstAddr.substring(2,4),16) & 0xFF);
		this.dstAddr[2] = (byte) (Integer.parseInt(dstAddr.substring(4,6),16) & 0xFF);
		sb.append(dstAddr.toUpperCase());

		this.groupid = groupId;
		sb.append(String.format("%02x", this.groupid).toUpperCase());

		this.rawMsg = sb.toString();
	}

	/**
	 * This constructor creates a raw message ready for sending.
	 * @param msgCount Message Counter
	 * @param msgFlag Message flag
	 * @param msgType the message type
	 * @param groupId Group ID
	 * @param srcAddr Source address of controller
	 * @param dstAddr Dest addr of device
	 * @param payload payload of message
	 */
	public BaseMsg(byte msgCount, byte msgFlag, MaxCulMsgType msgType, byte groupId, String srcAddr, String dstAddr, byte[] payload)
	{
		this(msgCount,msgFlag, msgType, groupId, srcAddr, dstAddr);
		appendPayload(payload);
	}

	protected void appendPayload(byte[] payload)
	{
		StringBuilder sb = new StringBuilder(this.rawMsg);
		this.flgReadyToSend = true;
		this.payload = payload;
		for (int byteIdx = 0; byteIdx < payload.length; byteIdx++) {
			sb.append(String.format("%02X", payload[byteIdx]).toUpperCase());
		}

		/* prepend length & Z command */
		byte len = (byte) ((sb.length() / PKT_CHARS_PER_BYTE) & 0xFF);
		if ((int)len * PKT_CHARS_PER_BYTE != sb.length())
		{
			this.flgReadyToSend = false;
			logger.error("Unable to build raw message. Length is not correct");
		}
		sb.insert(0, String.format("Zs%02X", len));

		this.rawMsg = sb.toString();
		this.flgReadyToSend = true;
	}

	private static boolean pktLenOk( String rawMsg )
	{
		int len = (byte) (Integer.parseInt(rawMsg.substring(PKT_POS_MSG_LEN, PKT_POS_MSG_LEN+PKT_POS_MSG_LEN_LEN),16) & 0xFF); /* length of packet */
		if (len != ((rawMsg.length()-(PKT_POS_MSG_START+PKT_CHARS_PER_BYTE))/PKT_CHARS_PER_BYTE)) /* account for preceding characters in the message and a byte at the end which i think is a checksum  */
		{
			logger.error("Unable to process packet "+rawMsg+". Length is not correct.");
			return false;
		}
		return true;
	}

	/**
	 * Return the type of message that has been received given the message string
	 * @param rawMsg Message string from CUL device
	 * @return MaxCulMsgType extracted from the message
	 */
	public static MaxCulMsgType getMsgType(String rawMsg)
	{
		if (pktLenOk(rawMsg) == false)
			return MaxCulMsgType.UNKNOWN;

		return MaxCulMsgType.fromByte(Byte.parseByte(rawMsg.substring(PKT_POS_MSG_TYPE,PKT_POS_MSG_TYPE+PKT_POS_MSG_TYPE_LEN),16));
	}

	public static boolean isForUs(String rawMsg, String addr)
	{
		if (pktLenOk(rawMsg) == false)
			return false; // length is wrong ignore packet

		return addr.equalsIgnoreCase(rawMsg.substring(PKT_POS_DST_ADDR,PKT_POS_DST_ADDR+PKT_POS_DST_ADDR_LEN));
	}

	public int requiredCredit()
	{
		/* length in bits = amount of credit needed
		 * length in bits = num chars * 4
		 * This is because each char represents 4 bits of a hex number. RawMsg length
		 * is decremented by one to account for the 'Z'
		 */
		int credit = (this.rawMsg.length()-1)*4;

		/* credit is in 10ms units, round up */
		credit = (int)Math.ceil(credit/10.0);

		return credit;
	}

	/**
	 * Indicate if this packet is complete
	 * @return true if packet is ready to send
	 */
	public boolean readyToSend()
	{
		return this.flgReadyToSend;
	}

	public boolean requireCallback;

	/**
	 * Function called when requireCallback is set and a response is received associated with
	 * this message
	 */
	public void callBack()
	{
		/* Do nothing */
	}
}
