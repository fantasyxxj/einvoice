package cn.yokaya.invoice.web;
import cn.yokaya.invoice.core.Result;
import cn.yokaya.invoice.core.ResultGenerator;
import cn.yokaya.invoice.service.InvoiceData;
import cn.yokaya.invoice.service.InvoiceService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
* Created by CodeGenerator on 2019/04/12.
*/
@RestController
@RequestMapping("/invoice")
public class InvoiceController {
    @Resource
    private InvoiceService invoiceService;

    
    

    @RequestMapping(value = "/extrat")
	public InvoiceData extrat(@RequestParam("f") MultipartFile file) 
			throws ServletException, IOException{
    	
    	System.out.println(file.getOriginalFilename());  
    	String filename = invoiceService.saveFile(file);
		return invoiceService.extract(filename);
	}
}
