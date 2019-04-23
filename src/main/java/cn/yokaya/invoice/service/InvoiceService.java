package cn.yokaya.invoice.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cn.yokaya.invoice.core.ServiceException;
import cn.yokaya.invoice.db.dao.Detail1Mapper;
import cn.yokaya.invoice.db.dao.DetailMapper;
import cn.yokaya.invoice.db.dao.InvoiceMapper;
import cn.yokaya.invoice.db.entity.Detail;
import cn.yokaya.invoice.db.entity.Detail1;

@Service
public class InvoiceService {
	private final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

	private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");

	@Resource
	private InvoiceExtractor extrator;
	
	@Resource
	private InvoiceMapper invoiceMapper;
	
	@Resource
	private DetailMapper detailMapper;
	
	@Resource
	private Detail1Mapper detail1Mapper;

	// 保存上传文件的主目录
	@Value("${uploads}")
	private String uploads;

	/**
	 * 保存发票文件到本地。返回文件名
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	public String saveFile(MultipartFile file) throws ServiceException {
		// 得到上传文件的保存目录，将上传的文件存放于WEB-INF目录下，不允许外界直接访问，保证上传文件的安全
		// 目录形式为：根目录/日期
		String savePath = uploads + "/" + format.format(new Date());
		File f = new File(savePath);
		// 判断上传文件的保存目录是否存在
		if (!f.exists() && !f.isDirectory()) {
			logger.debug(savePath + "目录不存在，需要创建");
			// 创建目录
			f.mkdir();
		}
		try {
			String path = savePath + "/" + file.getOriginalFilename();

			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(new File(path)));
			// System.out.println(file.getName());
			out.write(file.getBytes());
			out.flush();
			out.close();

			return path;

		} catch (Exception e) {
			throw new ServiceException("文件上传失败:" + e.getMessage(), e);

		}
		// request.setAttribute("message",message);
		// request.getRequestDispatcher("/message.jsp").forward(request, response);
	}

	@Transactional
	public InvoiceData extract(String filename) {
		InvoiceData data = null;
		try {
			data = extrator.extract(filename);
			try
			{
				invoiceMapper.insert(data.getInvoice());
				int seq = data.getInvoice().getSeq();
				//如果有明细，则插入
				if(data.getDetail() != null)
				{
					for(Object detail : data.getDetail())
					{
						if(data.getType().equals("00"))
						{
							((Detail)detail).setInvoiceSeq(seq);
							detailMapper.insert((Detail)detail);
						}else
						{
							((Detail1)detail).setInvoiceSeq(seq);
							detail1Mapper.insert((Detail1)detail);
						}
					}
				}
			}catch(Exception ee)
			{
				logger.warn("插入数据库失败：" + ee.getMessage(), ee);
			}
			data.setMessage("解析发票成功");
			data.setCode("0000");
		} catch (Exception e) {
			logger.error("解析发票失败：" + e.getMessage(), e);
			if (data == null)
				data = new InvoiceData();
			data.setMessage("解析发票失败");
			data.setCode("9999");
		}
		return data;
	}

}
