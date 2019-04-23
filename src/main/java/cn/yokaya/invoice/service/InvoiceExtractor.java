package cn.yokaya.invoice.service;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cn.yokaya.invoice.core.ServiceException;
import cn.yokaya.invoice.db.entity.Detail;
import cn.yokaya.invoice.db.entity.Detail1;
import cn.yokaya.invoice.db.entity.Invoice;

/**
 * 专用于处理电子发票识别的类
 * 
 * @author arthurlee
 *
 */

@Service
public class InvoiceExtractor {
	private final Logger logger = LoggerFactory.getLogger(InvoiceExtractor.class);

	private final static DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");

	public InvoiceData extract(String filename) throws Exception,ServiceException, IOException {

		Invoice invoice = new Invoice();
		
		invoice.setFile(filename);
		invoice.setUploadTime(format.format(new Date()));
		
		String allText = readText(invoice, filename);
		
		//判断发票类型：有“通行费”+“车牌号”两个关键字，判断为通行费。其他为一般发票
		if(allText.indexOf("通行费") > 0 && allText.indexOf("车牌号") > 0)
		{
			invoice.setType("01");
		}else
		{
			invoice.setType("00");
		}
		
		//选定关键字作为坐标参考点
		// 获取几个重点关键词的坐标，依靠坐标去调整位置
		String keyWords = "机器编号";
		// 用这个的y坐标，限定购买方、密码区的y坐标
		String pdfPath = filename;
		PdfBoxKeyWordPosition kwp = new PdfBoxKeyWordPosition(keyWords, pdfPath);
		List<float[]> list = kwp.getCoordinate();
		// 数组：1是x，0是y
		float[] jqbhCor = list.get(0);
		// System.out.println("关键字(机器编号)坐标：y:" + jqbhCor[0] + ", x:" + jqbhCor[1]);

		//原来用货物服务来做参考，发现有发票没有这个内容，改成用“税率”来参考
		String keyWords1 = "税率";
		// 用这个的y坐标，来限定明细的表格的y坐标
		PdfBoxKeyWordPosition kwp1 = new PdfBoxKeyWordPosition(keyWords1, pdfPath);
		List<float[]> list1 = kwp1.getCoordinate();
		float[] hwhysCor = list1.get(0);
		// System.out.println("关键字（货物或应税）坐标：y:" + hwhysCor[0] + ", x:" + hwhysCor[1]);

		String keyWords2 = "价税合计";
		//使用y坐标，作为销售方区域的y坐标参考
		PdfBoxKeyWordPosition kwp2 = new PdfBoxKeyWordPosition(keyWords2, pdfPath);
		List<float[]> list2 = kwp2.getCoordinate();
		float[] jshjCor = list2.get(0);
		// System.out.println("关键字（价税合计）坐标：y:" + jshjCor[0] + ", x:" + jshjCor[1]);

		String keyWords4 = "开户行及账号";
		// 用这个的x坐标，限定购买方和销售方的x坐标
		PdfBoxKeyWordPosition kwp4 = new PdfBoxKeyWordPosition(keyWords4, pdfPath);
		List<float[]> list4 = kwp4.getCoordinate();
		float[] gmfkhhjzhCor;
		float[] xsfkhhjzhCor;
		boolean isDefault = false;
		if (list4.size() < 2) {
			// 获取不到，有变态的发票没法匹配，设置默认值
			gmfkhhjzhCor = new float[] { 122, 51 };
			xsfkhhjzhCor = new float[] { 341, 51 };
			isDefault = true;
		} else {
			gmfkhhjzhCor = list4.get(0);
			xsfkhhjzhCor = list4.get(1);
		}

		//选定密码区的x坐标参考点
		// 找出“密码区”的坐标。密码区三个字是独立的，竖着的。需要每个字单独找出来，还有可能有重复的。
		PdfBoxKeyWordPosition k1 = new PdfBoxKeyWordPosition("密", pdfPath);
		// System.out.println("找密码");
		List<float[]> l1 = k1.getCoordinate();

		PdfBoxKeyWordPosition k2 = new PdfBoxKeyWordPosition("码", pdfPath);
		List<float[]> l2 = k2.getCoordinate();

		PdfBoxKeyWordPosition k3 = new PdfBoxKeyWordPosition("区", pdfPath);
		List<float[]> l3 = k3.getCoordinate();

		// 找出三个数组中x相同。定义为差值在5以内的。
		int maqX = 0;
		for (int i = 0; i < l1.size(); i++) {
			float x1 = l1.get(i)[1];
			for (int j = 0; j < l2.size(); j++) {
				float x2 = l2.get(j)[1];
				if (Math.abs(x1 - x2) < 5) {
					// 认为相同
					for (int k = 0; k < l3.size(); k++) {
						float x3 = l3.get(k)[1];
						if (Math.abs(x2 - x3) < 5) {
							maqX = Math.round((x1 + x2 + x3) / 3);
						}
					}
				}
			}
		}
		// System.out.println("密码区：" + maqX);
		// 给默认值
		if (maqX == 0)
			maqX = 370;

		/*
		// 找"规格型号"四个字，如果找不到，就找”规则型号“四个字。再找不到就默认值
		String keyWords5 = "规格型号";
		PdfBoxKeyWordPosition kwp5 = new PdfBoxKeyWordPosition(keyWords5, pdfPath);
		List<float[]> list5 = kwp5.getCoordinate();
		int ggxhX = 175;
		if (list5.size() < 1) {
			// 获取不到，就是发票有问题，现在碰到有写 规则型号的
			keyWords5 = "规则型号";
			kwp5 = new PdfBoxKeyWordPosition(keyWords5, pdfPath);
			list5 = kwp4.getCoordinate();
			if (list5.size() < 1) {
				// 还是找不到
				ggxhX = 175;
			} else {
				ggxhX = Math.round(list5.get(0)[1]);
			}
		} else {
			ggxhX = Math.round(list5.get(0)[1]);
		}
		// System.out.println("规格型号：" + ggxhX);
		 * 
		 */
		/*
		 * for(float[] f : list4) { System.out.println("关键字（开户行及账号）坐标：y:" + f[0] +
		 * ", x:" + f[1]); }
		 */
		
		//原来用规格型号来作为商品明细中名称和其他的x坐标分割。加上通行费发票之后，不能用这个。使用税价合计大写来作为坐标参考
		//lee 2019-4-16
		String keyWords5 = invoice.getJshjdx();
		PdfBoxKeyWordPosition kwp5 = new PdfBoxKeyWordPosition(keyWords5, pdfPath);
		List<float[]> list5 = kwp5.getCoordinate();
		int ggxhX = Math.round(list5.get(0)[1]);
		//System.out.println(ggxhX);
		// 坐标参考点获取完毕。下面开始按照坐标参考点切割区域读取内容

		PDDocument doc = PDDocument.load(new File(pdfPath));
		PDPageTree allPages = doc.getDocumentCatalog().getPages();
		PDPage firstPage = (PDPage) allPages.get(0);

		// 获取页面大小;找一个基准值，用来修正区域坐标
		int pageWidth = Math.round(firstPage.getCropBox().getWidth());
		int pageHeight = Math.round(firstPage.getCropBox().getHeight());
		
		// 开始按区域读取内容
		PDFTextStripperByArea stripper = new PDFTextStripperByArea();
		stripper.setSortByPosition(true);

		// 购买方：
		int x1 = Math.round(gmfkhhjzhCor[1]) - 15;   	//开户行及账号的x为参考
		int y1 = Math.round(jqbhCor[0]) + 10;			//机器编号的y坐标为参考
		int w1 = maqX - x1 - 5;							//密码区x坐标为参考
		int h1 = Math.round(gmfkhhjzhCor[0]) - y1 + 20;	//开户行及账号的y坐标为参考
		// System.out.println("x=" + x1 + ",y=" + y1 + ",w=" + w1 + ",h=" + h1);
		Rectangle gmf = new Rectangle(x1, y1, w1, h1);
		stripper.addRegion("GMF", gmf);

		// 销售方
		int x2 = Math.round(xsfkhhjzhCor[1]) - 15;		//开户行及账号为x参考
		int y2 = Math.round(jshjCor[0]) + 10;			//价税合计的y坐标为参考
		//int w2 = 310;	
		int w2 = maqX - x2 - 5;							//密码区的x为参考
		int h2 = Math.round(xsfkhhjzhCor[0]) - y2 + 20;	//开户行及账号的y为参考
		// System.out.println("x=" + x2 + ",y=" + y2 + ",w=" + w2 + ",h=" + h2);
		Rectangle xsf = new Rectangle(x2, y2, w2, h2);
		stripper.addRegion("XSF", xsf);

		// 密码区
		int x3 = maqX + 10;								//密码区的x为参考
		int y3 = Math.round(jqbhCor[0]) + 10;			//机器编号的y为参考
		int w3 = pageWidth - maqX - 10;					//页面宽度减去密码区的x坐标为参考
		int h3 = Math.round(hwhysCor[0]) - y3;			//用税率的y坐标减去机器编号的y坐标作为参考
		Rectangle mmq = new Rectangle(x3, y3, w3, h3);	
		stripper.addRegion("MMQ", mmq);

		// 购买明细,这里是除了名称之外的
		// 这里三个区域，要算出y，用“货物或应税”的y坐标做基础
		// 第二个基础，计算规格
		int x = ggxhX - 20;											//用加税合计的大写金额作为x坐标的参考
		int y = Math.round(hwhysCor[0]) + 10;						//用税额的y坐标作参考
		int h = Math.round(jshjCor[0]) - Math.round(hwhysCor[0]) -15;	//价税合计的y坐标减去税率的y坐标
		Rectangle gmxx = new Rectangle(x, y, pageWidth, h);
		stripper.addRegion("GMXX", gmxx);
		//System.out.println("x=" + x + ",y=" + y + ",w=" + pageWidth + ",h=" + h);

		// 购买明细中的名称
		Rectangle spmc = new Rectangle(0, y, x, h);		//
		stripper.addRegion("SPMC", spmc);

		// TODO:其他区域
		stripper.extractRegions(firstPage);

		// 提取内容
		// 去空格（中文和英文的空格），替换中文冒号为西文冒号
		String gmfString = stripper.getTextForRegion("GMF").replaceAll(" ", "").replaceAll("　", "").replaceAll("：", ":")
				.replaceAll(" ", "");
		// System.out.println("------------购买方-------------------");
		// System.out.println(gmfString);
		String reg1 = "名称:(?<mc>\\S*)|纳税人识别号:(?<nsrsbh>\\S*)|地址、电话:(?<dzdh>\\S*)|开户行及账号:(?<khhjzh>\\S*)";
		Pattern patternGmf = Pattern.compile(reg1);
		Matcher matcherGmf = patternGmf.matcher(gmfString);
		// System.out.println("===========购买方:================");
		while (matcherGmf.find()) {
			// System.out.println(matcherGmf.group(0));
			if (matcherGmf.group("mc") != null) {
				invoice.setMc1(matcherGmf.group("mc"));
			} else if (matcherGmf.group("nsrsbh") != null) {
				invoice.setNsrsbh1(matcherGmf.group("nsrsbh"));
			} else if (matcherGmf.group("dzdh") != null) {
				invoice.setDzdh1(matcherGmf.group("dzdh"));
			} else if (matcherGmf.group("khhjzh") != null) {
				invoice.setKhhjzh1(matcherGmf.group("khhjzh"));
			}

		}
		// System.out.println("===========购买方提取信息完毕================");

		// TODO:地址折行的情况，有问题，考虑模式识别地址
		String xsfString = stripper.getTextForRegion("XSF").replaceAll(" ", "").replaceAll("　", "").replaceAll("：", ":")
				.replaceAll(" ", "");
		// System.out.println("------------销售方-------------------");
		// System.out.println(xsfString);
		String reg2 = "名称:(?<mc>\\S*)|纳税人识别号:(?<nsrsbh>\\S*)|地址、电话:(?<dzdh>\\S*)|开户行及账号:(?<khhjzh>\\S*)";
		Pattern patternXsf = Pattern.compile(reg2);
		Matcher matcherXsf = patternXsf.matcher(xsfString);
		// System.out.println("===========销售方:================");
		while (matcherXsf.find()) {
			// System.out.println(matcherXsf.group(0));
			if (matcherXsf.group("mc") != null) {
				invoice.setMc2(matcherXsf.group("mc"));
			} else if (matcherXsf.group("nsrsbh") != null) {
				invoice.setNsrsbh2(matcherXsf.group("nsrsbh"));
			} else if (matcherXsf.group("dzdh") != null) {
				invoice.setDzdh2(matcherXsf.group("dzdh"));
			} else if (matcherXsf.group("khhjzh") != null) {
				invoice.setKhhjzh2(matcherXsf.group("khhjzh"));
			}
		}
		// System.out.println("===========销售方提取信息完毕================");

		String mmqString = stripper.getTextForRegion("MMQ").replaceAll(" ", "").replaceAll("　", "");
		// 过滤不需要的，包括第一行可能是校验码，最后一行空行
		String[] passArr = mmqString.split("\\s");
		String newMmqString = "";
		for (int i = 0; i < passArr.length; i++) {
			String pass = passArr[i];
			if (pass.startsWith("校") || pass.length() < 1)
				continue;
			// 先把中文去掉
			String rr = "[\u4e00-\u9fa5]";
			Pattern pat = Pattern.compile(rr);
			Matcher mat = pat.matcher(pass);
			pass = mat.replaceAll("");

			if (pass.startsWith("校") || pass.length() < 1)
				continue;
			newMmqString += pass;
			if (i < passArr.length - 1)
				newMmqString += "\n";
		}
		// System.out.println("===========密码区================");
		// System.out.println(newMmqString);
		invoice.setMmq(newMmqString);
		// System.out.println("===========密码区完毕================");

		// 交易明细这里只处理除商品名称之外的列，相当于少一列
		String gmxxString = stripper.getTextForRegion("GMXX").replaceAll("　", " ");
		// System.out.println("------------购买明细-------------------");
		// System.out.println(gmxxString);
		ArrayList<Map<String, Object>> itemList = new ArrayList<>();
		// 拆开行
		String[] gmxxArr = gmxxString.split("\\n|\\r");
		// 金额合计
		BigDecimal amount = new BigDecimal(0);
		// 税额合计
		BigDecimal tax = new BigDecimal(0);
		for (int i = 0; i < gmxxArr.length; i++) {
			// 清除不是合法的行
			if (gmxxArr[i].replaceAll(" ", "").matches("\\S*(金额|税率|税额|¥|￥)\\S*") || gmxxArr[i].length() < 2)
				continue;
			// System.out.println(gmxxArr[i]);
			// 目前发现，只有最后3个，分别是金额、税额是必须有的。并且税率出现“免税”字样，当免税的时候，税额出现过“***"，暂不知是否有其他字样
			// 查看规范，有：免税，不征税，出口零税率，普通零税率 四种
			// 税率可能是空，附带明细表述各种商品的税率，这里做一个特殊处理，这种情况，只能有两个：金额和税额，其他都不能有
			String[] itemArr = gmxxArr[i].split("\\s");
			HashMap<String, Object> items = new HashMap<>();
			//特殊情况，只有两列，就必须是金额和税额
			if (itemArr.length == 2) {
				items.put("je", new BigDecimal(itemArr[0]));
				items.put("se", new BigDecimal(itemArr[1]));
				amount = amount.add((BigDecimal) items.get("je"));
				tax = tax.add((BigDecimal) items.get("se"));
				itemList.add(items);
				continue;
			}

			//处理 金额、税率、税额
			// 先处理倒数第三个 金额
			items.put("je", new BigDecimal(itemArr[itemArr.length - 3]));
			// 处理倒数第二个 税率，倒数第一个 税额
			// 如果不是%，就是免税
			if (itemArr[itemArr.length - 2].indexOf("免税") > 0 || itemArr[itemArr.length - 2].indexOf("不征税") > 0
					|| itemArr[itemArr.length - 2].indexOf("出口零税率") > 0
					|| itemArr[itemArr.length - 2].indexOf("普通零税率") > 0
					|| itemArr[itemArr.length - 2].indexOf("%") < 0) {
				// 免税，设置为0
				items.put("sl", new BigDecimal(0));
				items.put("se", new BigDecimal(0));
			} else {
				// 有税率
				BigDecimal sl = new BigDecimal(Integer.parseInt(itemArr[itemArr.length - 2].replaceAll("%", "")));
				// 除100
				sl = sl.divide(new BigDecimal(100));
				items.put("sl", sl);
				items.put("se", new BigDecimal(itemArr[itemArr.length - 1]));
			}

			// 表示找到的第几个数字
			int numIndex = 1;
			for (int j = 0; j < itemArr.length - 3; j++) {
				// System.out.println(itemArr[j]);
				if (itemArr[j].matches("^(-?\\d+)(\\.\\d+)?$")) {
					// 如果找到了数字，看是第几个，如果是第一个，就是 数量，第二个就是单价
					if (numIndex == 1) {
						items.put("liang", itemArr[j]);
						numIndex++;
					} else {
						items.put("dj", itemArr[j]);
					}
				} else {
					// 如果找到第一个文字，则看下面一个是否也是文字，如果也是，就是 规格和单位，如果只有一个，默认放到单位（目前样本看来，单位的更多）
					if (itemArr.length >= j + 1 && !itemArr[j + 1].matches("^(-?\\d+)(\\.\\d+)?$")) {
						items.put("dw", itemArr[j + 1]);
						items.put("ggxh", itemArr[j]);
						j++;
					} else {
						items.put("dw", itemArr[j]);
					}
				}
			}

			amount = amount.add((BigDecimal) items.get("je"));
			tax = tax.add((BigDecimal) items.get("se"));
			itemList.add(items);
		}
		// 处理商品名称。
		String spmcString = stripper.getTextForRegion("SPMC").replaceAll(" ", "").replaceAll("　", "");

		// System.out.println("========商品名称========");
		// System.out.println(spmcString);
		// 根据上面的itemsList判断名称应该有几行
		String[] nameArr = spmcString.split("\\n|\\r");
		ArrayList<String> nameArrList = new ArrayList<>();
		// 先过滤名称的多余内容，变成单纯的名称的内容
		for (int i = 0; i < nameArr.length; i++) {
			if (nameArr[i].indexOf("货物或") >= 0 || nameArr[i].indexOf("项目名称") >= 0 
					|| nameArr[i].indexOf("开户行") >= 0 
					|| nameArr[i].replaceAll(" ", "").indexOf("合计") >= 0
					|| nameArr[i].trim().length() == 0)
				continue;
			nameArrList.add(nameArr[i]);
		}
		// 先结束这次文件读取
		doc.close();

		// 重新打开一次文件，读取全部交易明细
		PDDocument doc1 = PDDocument.load(new File(pdfPath));

		PDFTextStripperByArea stripper1 = new PDFTextStripperByArea();
		stripper1.setSortByPosition(true);

		PDPageTree allPages1 = doc1.getDocumentCatalog().getPages();
		PDPage firstPage1 = (PDPage) allPages1.get(0);
		// 购买明细的全部，包含名称和价格等
		Rectangle qbmx = new Rectangle(0, y, pageWidth, h);
		stripper1.addRegion("QBMX", qbmx);
		stripper1.extractRegions(firstPage1);

		// 拿到全部的明细，包含名称和价格，用以处理真实的商品名称：后代带有税率价格的，是商品名称的第一行，没有的，则是第二行
		String qbmxString = stripper1.getTextForRegion("QBMX").replaceAll(" ", "").replaceAll("　", "");
		doc1.close();
		// System.out.println(qbmxString);
		// 理论上nameArr和 mxArr是一样多的行数
		String[] mxArr = qbmxString.split("\\n|\\r");
		// 滤清明细表格内容
		ArrayList<String> mxArrList = new ArrayList<>();
		for (int i = 0; i < mxArr.length; i++) {
			if (mxArr[i].indexOf("单价金额") > 0 || mxArr[i].indexOf("开户行") >= 0 
					|| mxArr[i].indexOf("合计") >= 0
					|| mxArr[i].trim().length() == 0)
				continue;
			mxArrList.add(mxArr[i]);
		}
		String spName = "";
		ArrayList<String> nameList = new ArrayList<>();
		 System.out.println(mxArrList);
		 System.out.println(nameArrList);
		for (int i = 0; i < mxArrList.size(); i++) {
			// System.out.println("------" + mxArr[i]);
			if (mxArrList.get(i).matches("\\S+\\d*(%|免税|不征税|出口零税率|普通零税率)\\S*")) {
				// System.out.println("找到第一行");
				if (!spName.equals("")) {
					nameList.add(spName);
				}
				// 判断为名称的第一行
				spName = nameArrList.get(i);
			} else {
				spName += nameArrList.get(i);
			}
		}
		nameList.add(spName);
		// System.out.println(nameList);
		// 把名称放到完整数组里面
		for (int i = 0; i < nameList.size(); i++) {
			if (i >= itemList.size())
				continue;
			itemList.get(i).put("hwmc", nameList.get(i));
		}
		
		invoice.setHjje(amount);
		invoice.setHjse(tax);
		
		
		if(invoice.getType().equals("01"))
		{
			InvoiceData<Detail1> invoiceData = new InvoiceData<>();
			invoiceData.setInvoice(invoice);
			invoiceData.setType(invoice.getType());
			for (Map<String, Object> item : itemList) 
			{
				Detail1 detail1 = new Detail1();
				detail1.setXmmc((String) item.get("hwmc"));
				detail1.setCph((String) item.get("ggxh"));
				detail1.setLx((String) item.get("dw"));
				detail1.setRqq((String)item.get("liang"));
				detail1.setRqz((String)item.get("dj"));
				detail1.setJe((BigDecimal) item.get("je"));
				detail1.setSl((BigDecimal) item.get("sl"));
				detail1.setSe((BigDecimal) item.get("se"));
	
				invoiceData.addDetail(detail1);
			}
			return invoiceData;
		}else
		{
			InvoiceData<Detail> invoiceData = new InvoiceData<>();
			invoiceData.setInvoice(invoice);
			invoiceData.setType(invoice.getType());
			for (Map<String, Object> item : itemList) 
			{
				Detail detail = new Detail();
				detail.setWwmc((String) item.get("hwmc"));
				detail.setGgxh((String) item.get("ggxh"));
				detail.setDw((String) item.get("dw"));
				if(item.get("liang") != null)
				{
					detail.setCount(new BigDecimal((String)item.get("liang")));
				}
				if(item.get("dj") != null)
				{
					detail.setDj(new BigDecimal((String) item.get("dj")));
				}
				detail.setJe((BigDecimal) item.get("je"));
				detail.setSl((BigDecimal) item.get("sl"));
				detail.setSe((BigDecimal) item.get("se"));
	
				invoiceData.addDetail(detail);
			}
			return invoiceData;
		}
		// System.out.println("===============交易明细结束============");
		// System.out.println("===============交易汇总数据============");
		// System.out.println("不含税总金额：" + amount + ";税额总额：" + tax);
		
		
		// System.out.println("===============交易汇总数据结束============");
	}

	
	/**
	 * 对pdf文件读出所有的文本，用正则表达式先处理可以处理的部分，并返回文本全文
	 * @param invoice
	 * @param pdfPath
	 * @return
	 * @throws Exception
	 */
	private String readText(Invoice invoice, String pdfPath) throws Exception {
		// 开始读文字
		PDDocument doc = PDDocument.load(new File(pdfPath));
		PDPageTree allPages = doc.getDocumentCatalog().getPages();
		PDPage firstPage = (PDPage) allPages.get(0);

		// 获取页面大小;找一个基准值，用来修正区域坐标
		int pageWidth = Math.round(firstPage.getCropBox().getWidth());
		int pageHeight = Math.round(firstPage.getCropBox().getHeight());
		boolean isRotation = false;
		// 如果旋转过，就交换长宽
		if (firstPage.getRotation() != 0) {
			pageWidth = Math.round(firstPage.getCropBox().getHeight());
			pageHeight = Math.round(firstPage.getCropBox().getWidth());
			isRotation = true;
		}
		logger.debug("文件名：" + pdfPath + "l是否旋转：" + isRotation + ";页面宽度：" + pageWidth + "；页面高度：" + pageHeight);
		// 先读出完整的内容
		PDFTextStripper textStripper = new PDFTextStripper();
		textStripper.setSortByPosition(true);
		// stripper.setLineSeparator("|");

		String allText = textStripper.getText(doc);
		// 去除空格
		allText = allText.replaceAll(" ", "").replaceAll("　", "").replaceAll("（", "(").replaceAll("）", ")")
				.replaceAll("￥", "¥").replaceAll("：", ":").replaceAll(" ", "");
		// 把中文冒号变成英文冒号
		allText = allText.replaceAll("：", ":");
		// System.out.println("===========全部文本===========");
		logger.debug("文件[" + pdfPath + "]内容:\n" + allText);

		// 通过全文中可以识别的内容先进行识别；
		// 提取发票标题
		String rr3 = "\\S*普通发票";
		Pattern p3 = Pattern.compile(rr3);
		Matcher m3 = p3.matcher(allText);
		if (m3.find()) {
			invoice.setTitle(m3.group(0));
			// logger.debug(m3.group(0));
		} else {
			throw new ServiceException("不是发票文件");
		}
		// 提取机器编号等
		String reg = "机器编号:(?<jqbh>\\d{12})|发票代码:(?<fpdm>\\d{12})|发票号码:(?<fphm>\\d{8})"
				+ "|开票日期:(?<kprq>\\d{4}年\\d{2}月\\d{2}日)" + "|校验码:(?<jym>\\d{20})";
		Pattern pattern = Pattern.compile(reg);
		Matcher matcher = pattern.matcher(allText);
		// System.out.println("===========汇总信息================");
		while (matcher.find()) {
			// System.out.println(matcher.group(0));
			if (matcher.group("jqbh") != null) {
				invoice.setJqbh(matcher.group("jqbh"));
			} else if (matcher.group("fpdm") != null) {
				invoice.setFpdm(matcher.group("fpdm"));
			} else if (matcher.group("fphm") != null) {
				invoice.setFphm(matcher.group("fphm"));
			} else if (matcher.group("kprq") != null) {
				invoice.setKprq(matcher.group("kprq"));
			} else if (matcher.group("jym") != null) {
				invoice.setJym(matcher.group("jym"));
			}

		}
		// 识别合计金额
		String rr1 = "价税合计\\u0028大写\\u0029(?<dxje>\\S*)\\u0028小写\\u0029¥(?<xxje>\\S*)\\s";
		Pattern p1 = Pattern.compile(rr1);
		Matcher m1 = p1.matcher(allText);
		if (m1.find()) {
			// System.out.println("价税合计：" + m1.group("dxje"));
			// System.out.println("小写金额：" + m1.group("xxje"));
			invoice.setJshjdx(m1.group("dxje"));
			try {
				invoice.setJshjxx(new BigDecimal(m1.group("xxje")));
			} catch (Exception e) {
				invoice.setJshjxx(new BigDecimal(0));
			}
		}
		// 识别底部
		String rr2 = "收款人:(?<skr>\\S*)复核:(?<fh>\\S*)开票人:(?<kpr>\\S*)销售方";
		Pattern p2 = Pattern.compile(rr2);
		Matcher m2 = p2.matcher(allText);
		if (m2.find()) {
			// System.out.println("收款人：" + m2.group("skr"));
			// System.out.println("复核：" + m2.group("fh"));
			// System.out.println("开票人：" + m2.group("kpr"));
			invoice.setSkr(m2.group("skr"));
			invoice.setFh(m2.group("fh"));
			invoice.setKpr(m2.group("kpr"));
		}
		doc.close();
		return allText;
	}
}
