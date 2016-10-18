package com.pdm.spectrogram.utils;

import java.io.IOException;
import java.io.InputStream;
/**
 * Author:pdm on 2016/3/15
 * Email:aiyh0202@163.com
 * CSDN:http://blog.csdn.net/aiyh0202
 * GitHub:https://github.com/flyingfishes
 * 这里是解析wav的标签头，因为写的
 */
public class WaveFileReader
{
	private String filename = "文件";

	private int len = 0;

	private String chunkdescriptor = null;
	static private int lenchunkdescriptor = 4;

	private long chunksize = 0;
	static private int lenchunksize = 4;

	private String waveflag = null;
	static private int lenwaveflag = 4;

	private String fmtubchunk = null;
	static private int lenfmtubchunk = 4;

	private long subchunk1size = 0;
	static private int lensubchunk1size = 4;

	private int audioformat = 0;
	static private int lenaudioformat = 2;

	private int numchannels = 0;
	static private int lennumchannels = 2;

	private long samplerate = 0;
	static private int lensamplerate = 2;

	private long byterate = 0;
	static private int lenbyterate = 4;

	private int blockalign = 0;
	static private int lenblockling = 2;

	private int bitspersample = 0;
	static private int lenbitspersample = 2;

	private String datasubchunk = null;
	static private int lendatasubchunk = 4;

	private long subchunk2size = 0;

	private InputStream bis = null;

	private boolean issuccess = false;
	// 获取数据
	// 数据是一个二维数组，[n][m]代表第n个声道的第m个采样值

	// 判断是否创建wav读取器成功
	public boolean isSuccess() 
	{
		return issuccess;
	}

	// 获取每个采样的编码长度，8bit或者16bit
	public int getBitPerSample() 
	{
		return this.bitspersample;
	}

	// 获取采样率
	public long getSampleRate() 
	{
		return this.samplerate;
	}

	// 获取声道个数，1代表单声道 2代表立体声
	public int getNumChannels()
	{
		return this.numchannels;
	}

	// 获取数据长度，也就是一共采样多少个
	public int getDataLen()
	{
		return this.len;
	}

	public int[][] initReader(InputStream inputStream)
	{
		byte[] 	Byte_buf = new byte[256];
		long	File_Len;
		int 	Audio_Index;
		long 	Block_Nr;
		long 	j;
		long	k;
		int		l;
		int[][] data = null;
		try 
		{
			bis = inputStream;

			this.chunkdescriptor = readString(lenchunkdescriptor);
			
			if (!chunkdescriptor.endsWith("RIFF"))
			{
				throw new IllegalArgumentException("RIFF miss, " + filename
						+ " is not a wave file.");
			}
			
			this.chunksize = readLong();
			this.waveflag = readString(lenwaveflag);
			if (!waveflag.endsWith("WAVE"))
			{
				throw new IllegalArgumentException("WAVE miss, " + filename
						+ " is not a wave file.");
			}

			this.fmtubchunk = readString(lenfmtubchunk);
			if (!fmtubchunk.endsWith("fmt "))
			{
				throw new IllegalArgumentException("fmt miss, " + filename
						+ " is not a wave file.");
			}

			this.subchunk1size = readLong();
			this.audioformat = readInt();
			this.numchannels = readInt();
			this.samplerate = readLong();
			this.byterate = readLong();
			this.blockalign = readInt();
			this.bitspersample = readInt();

			this.datasubchunk = readString(lendatasubchunk);
			if (!datasubchunk.endsWith("data"))
			{
				throw new IllegalArgumentException("data miss, " + filename
						+ " is not a wave file.");
			}
			this.subchunk2size = readLong();

			this.len = (int) (this.subchunk2size / (this.bitspersample / 8) / this.numchannels);
			
			data = new int[this.numchannels][this.len];
			
			if (this.bitspersample == 8) 
			{
				for (int i = 0; i < this.len; ++i) 
				{
					for (int n = 0; n < this.numchannels; ++n) 
					{
						data[n][i] = bis.read();
					} 

				}
			}
			else if (this.bitspersample == 16) 
			{
				File_Len = this.len * this.numchannels * 2;
				Audio_Index = 0;
				for(Block_Nr = (File_Len + 255) / 256;Block_Nr > 0;--Block_Nr)
				{
					if(File_Len < 256)
					{
						Byte_buf = new byte[(int)File_Len];
						bis.read(Byte_buf);
						j = File_Len;
					}
					else
					{
						bis.read(Byte_buf);
						j = 256;
					}

					l = 0;
					while (l < j) 
					{
						for(int ch = 0;ch < this.numchannels;++ch)
						{
							k = ((long)Byte_buf[l + 1] << 8) + Byte_buf[l];
							data[ch][Audio_Index] = (int)k;
							l += 2;
						}
						++Audio_Index;
					} 
					File_Len -= 256;
				}
			}			
			issuccess = true;
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		} 
		finally 
		{
			try 
			{
				if (bis != null)
				{
					bis.close();
				}
			} 
			catch (Exception e1)
			{
				e1.printStackTrace();
			}
		}
		return data;
	}

	private String readString(int len)
	{
		byte[] buf = new byte[len];
		try {
			if (bis.read(buf) != len)
			{
				throw new IOException("no more data!!!");
			}
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return new String(buf);
	}

	private int readInt() 
	{
		byte[] buf = new byte[2];
		int res = 0;
		try 
		{
			if (bis.read(buf) != 2)
			{
				throw new IOException("no more data!!!");
			}
			res = (buf[0] & 0x000000FF) | (((int) buf[1]) << 8);
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return res;
	}

	private long readLong() {
		long res = 0;
		try {
			long[] l = new long[4];
			for (int i = 0; i < 4; ++i) 
			{
				l[i] = bis.read();
				if (l[i] == -1) 
				{
					throw new IOException("no more data!!!");
				}
			}
			res = l[0] | (l[1] << 8) | (l[2] << 16) | (l[3] << 24);
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return res;
	}

	private byte[] readBytes(int len) 
	{
		byte[] buf = new byte[len];
		try {
			if (bis.read(buf) != len)
			{
				throw new IOException("no more data!!!");
			}
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return buf;
	}
}
