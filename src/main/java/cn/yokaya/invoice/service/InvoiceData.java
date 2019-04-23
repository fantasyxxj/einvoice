package cn.yokaya.invoice.service;

import java.util.ArrayList;
import java.util.List;

import cn.yokaya.invoice.db.entity.Detail;
import cn.yokaya.invoice.db.entity.Invoice;

public class InvoiceData <T>{

	private Invoice invoice;
	
	private List<T> detailList = null;
	
	private String message;
	
	private String code;
	
	
	private String status;
	
	private String type;

	public Invoice getInvoice() {
		return invoice;
	}

	public void setInvoice(Invoice invoice) {
		this.invoice = invoice;
	}

	public List<T> getDetail() {
		return detailList;
	}

	public void setDetail(List<T> detailList) {
		this.detailList = detailList;
	}
	
	public void addDetail(T detail)
	{
		if(detailList == null)
		{
			detailList = new ArrayList<T>();
		}
		detailList.add(detail);
	}
	
	public void setMessage(String message)
	{
		this.message = message;
	}
	
	public String getMessage()
	{
		return this.message;
	}
	
	public void setCode(String code)
	{
		this.code = code;
	}
	
	public String getCode()
	{
		return code;
	}
	
	public void setStatus(String status)
	{
		this.status = status;
	}
	
	/**
	 * 发票的状态：有效，签章无效，文件被修改
	 * @return
	 */
	public String getStatus()
	{
		return this.status;
	}
	
	public void setType(String type)
	{
		this.type = type;
	}
	
	/**
	 * 记录发票类型，目前支持两种类型：00：一般发票；01通行费发票
	 * @return
	 */
	public String getType()
	{
		return this.type;
	}
}
