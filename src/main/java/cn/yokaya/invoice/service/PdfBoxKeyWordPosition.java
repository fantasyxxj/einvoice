package cn.yokaya.invoice.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
 
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
 
public class PdfBoxKeyWordPosition extends PDFTextStripper {
 
	//关键字字符数组
	private char[] key;
	//PDF文件路径
	private String pdfPath;
	//坐标信息集合
	private List<float[]> list = new ArrayList<float[]>();
	//当前页信息集合
	private List<float[]> pagelist = new ArrayList<float[]>();
 
	//有参构造方法
	public PdfBoxKeyWordPosition(String keyWords, String pdfPath) throws IOException {
		super();
		super.setSortByPosition(true);
		this.pdfPath = pdfPath;
 
		char[] key = new char[keyWords.length()];
		for (int i = 0; i < keyWords.length(); i++) {
			key[i] = keyWords.charAt(i);
		}
		this.key = key;
	}
	
	public char[] getKey() {
		return key;
	}
 
	public void setKey(char[] key) {
		this.key = key;
	}
 
	public String getPdfPath() {
		return pdfPath;
	}
 
	public void setPdfPath(String pdfPath) {
		this.pdfPath = pdfPath;
	}
 
	//获取坐标信息
	public List<float[]> getCoordinate() throws IOException {
		try {
			document = PDDocument.load(new File(pdfPath));
			int pages = document.getNumberOfPages();
 
			for (int i = 1; i <= pages; i++) {
				pagelist.clear();
				super.setSortByPosition(true);
				super.setStartPage(i);
				super.setEndPage(i);
				Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
				super.writeText(document, dummy);
				for (float[] li : pagelist) {
					li[2] = i;
				}
				list.addAll(pagelist);
			}
			return list;
 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (document != null) {
				document.close();
			}
		}
		return list;
	}
 
	private int foundIndex = 0;
	private List<TextPosition> foundPositon = new ArrayList<>();
	//获取坐标信息
	@Override
	protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
		for (int i = 0; i < textPositions.size(); i++) {
 
			String str = textPositions.get(i).getUnicode();
			if (str.equals(key[foundIndex] + "")) {
				foundIndex++;
				foundPositon.add(textPositions.get(i));
				int count = foundIndex;
				int k = 1;
				for (int j = foundIndex; j < key.length; j++) {
					String s = "";
					if(i+j >= textPositions.size())
						break;
					else
						s = textPositions.get(i + j).getUnicode();
					
					//System.out.println(s);
					if (s.equals(key[j] + "")) {
						count++;
					}
 
				}
				if (count == key.length) {
					foundIndex = 0;
					float[] idx = new float[3];
					//idx[0] = textPositions.get(i).getX() + key.length * textPositions.get(i).getWidth() / 2;
					//idx[1] = textPositions.get(i).getY() - textPositions.get(i).getHeight();
					//idx[2] = textPositions.get(i).getUnicode();
					//关键字到Y轴的距离=>X坐标
					//idx[0] = textPositions.get(i).getY() - 2 * textPositions.get(i).getHeight();
					//idx[0] = textPositions.get(0).getY();
					idx[0] = foundPositon.get(0).getY();
					//关键字到X轴的距离=>Y坐标
					//idx[1] = textPositions.get(0).getX();
					idx[1] = foundPositon.get(0).getX();
					pagelist.add(idx);
				}
			}else
			{
				foundPositon.clear();
			}
 
		}
	}

}