package com.fuwei.service;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fuwei.commons.Pager;
import com.fuwei.commons.Sort;
import com.fuwei.constant.OrderStatus;
import com.fuwei.entity.Order;
import com.fuwei.entity.OrderDetail;
import com.fuwei.entity.OrderHandle;
import com.fuwei.entity.OrderProduceStatus;
import com.fuwei.entity.ProductionNotification;
import com.fuwei.entity.QuoteOrder;
import com.fuwei.entity.Sample;
import com.fuwei.util.CreateNumberUtil;
import com.fuwei.util.DateTool;
import com.fuwei.constant.OrderStatusUtil;

@Component
public class OrderService extends BaseService {
	private Logger log = org.apache.log4j.LogManager
			.getLogger(QuoteOrderService.class);
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OrderDetailService orderDetailService;
	@Autowired
	OrderHandleService orderHandleService;
	@Autowired
	OrderProduceStatusService orderProduceStatusService;
	@Autowired
	ProductionNotificationService productionNotificationService;
	// 获取订单列表
	public Pager getList(Pager pager, Date start_time, Date end_time,
			Integer companyId, Integer salesmanId,Integer status, List<Sort> sortlist)
			throws Exception {
		try {
			StringBuffer sql = new StringBuffer();
			String seq = "WHERE ";
			if (companyId != null & salesmanId == null) {
				sql.append("select * from tb_order where companyId='"
						+ companyId + "'");
				seq = " AND ";
			} else {
				sql.append("select * from tb_order ");
			}

			if (start_time != null) {
				sql.append(seq + " created_at>='"
						+ DateTool.formateDate(start_time) + "'");
				seq = " AND ";
			}
			if (end_time != null) {
				sql.append(seq + " created_at<='"
						+ DateTool.formateDate(DateTool.addDay(end_time, 1))
						+ "'");
				seq = " AND ";
			}
			if (salesmanId != null) {
				sql.append(seq + " salesmanId='" + salesmanId + "'");
				seq = " AND ";
			}
			if(status!=null){
				sql.append(seq + " status='" + status + "'");
			}

			if (sortlist != null && sortlist.size() > 0) {

				for (int i = 0; i < sortlist.size(); ++i) {
					if (i == 0) {
						sql.append(" order by " + sortlist.get(i).getProperty()
								+ " " + sortlist.get(i).getDirection() + " ");
					} else {
						sql.append("," + sortlist.get(i).getProperty() + " "
								+ sortlist.get(i).getDirection() + " ");
					}

				}
			}
			return findPager_T(sql.toString(), Order.class, pager);
		} catch (Exception e) {
			throw e;
		}
	}

	// 添加订单,返回主键
	@Transactional
	public int add(Order order, OrderHandle handle) throws Exception {
		try {

			if (order.getDetaillist() == null
					|| order.getDetaillist().size() <= 0) {
				throw new Exception("订单单中至少得有一条样品记录");
			} else {
				Integer orderId = this.insert(order);
				String orderNumber = CreateNumberUtil
						.createFWStyleNumber(orderId);
				order.setOrderNumber(orderNumber);
				order.setId(orderId);
				this.update(order, "id", null);
				for (OrderDetail detail : order.getDetaillist()) {
					detail.setOrderId(orderId);
				}
				orderDetailService.addBatch(order.getDetaillist());

				// 添加操作记录
				handle.setOrderId(orderId);
				orderHandleService.add(handle);

				return orderId;
			}
		} catch (Exception e) {

			throw e;
		}
	}

	// 删除订单
	public int remove(int id) throws Exception {
		try {
			return dao.update("delete from tb_order WHERE  id = ?", id);
		} catch (Exception e) {
			SQLException sqlException = (java.sql.SQLException) e.getCause();
			if (sqlException != null && sqlException.getErrorCode() == 1451) {// 外键约束
				log.error(e);
				throw new Exception("订单已被引用，无法删除，请先删除引用");
			}
			throw e;
		}
	}
	
	// 根据detailId获取订单
	public Order getByDetailId(int detailId) throws Exception {
		try {
			Order order = dao.queryForBean(
					"select o.* from tb_order o ,tb_order_detail d where o.id=d.orderId AND d.id = ?", Order.class, detailId);
			return order;
		} catch (Exception e) {
			throw e;
		}
	}
	
	// 获取订单
	public Order get(int id) throws Exception {
		try {
			Order order = dao.queryForBean(
					"select * from tb_order where id = ?", Order.class, id);
			return order;
		} catch (Exception e) {
			throw e;
		}
	}
	
	// 编辑订单
	@Transactional
	public int update(Order order, OrderHandle handle) throws Exception {
		try {
			// 更新订单表
			this.update(order, "id",
					"created_user,status,state,created_at,orderNumber,stepId,setp_state", true);
			// 删除原来订单的detail
			orderDetailService.deleteBatch(order.getId());
			// 再添加新的detail
			orderDetailService.addBatch(order.getDetaillist());

			// 添加操作记录
			orderHandleService.add(handle);

			return order.getId();
		} catch (Exception e) {
			throw e;
		}

	}

	// 添加步骤
	public int addstep(OrderProduceStatus orderProduceStatus, OrderHandle handle)
			throws Exception {
		try {
			int primaryId = orderProduceStatusService.add(orderProduceStatus);
			// 添加操作记录
			orderHandleService.add(handle);
			return primaryId;
		} catch (Exception e) {
			throw e;
		}
	}
	// 修改步骤
	public int updatestep(OrderProduceStatus orderProduceStatus, OrderHandle handle)
			throws Exception {
		try {
			int success = orderProduceStatusService.update(orderProduceStatus);
			//修改当前订单的step_state描述
			Order order = this.get(orderProduceStatus.getOrderId());
			if(order.getStepId()!=null && order.getStepId() == orderProduceStatus.getId() && !order.getStep_state().equals(orderProduceStatus.getName())){
				order.setStep_state(orderProduceStatus.getName());
				this.update(order, "id",
						"created_user,status,state,created_at,orderNumber,stepId", true);
			}
			// 添加操作记录
			orderHandleService.add(handle);
			return success;
		} catch (Exception e) {
			throw e;
		}
	}
	// 删除步骤
	public int deletestep(int stepId, OrderHandle handle)
			throws Exception {
		try {
			int success = orderProduceStatusService.remove(stepId);
			// 添加操作记录
			orderHandleService.add(handle);
			return success;
		} catch (Exception e) {
			throw e;
		}
	}
	
	//执行订单
	@Transactional
	public int exestep(int orderId,OrderHandle handle)
			throws Exception {
		try {
			//获取当前步骤
			Order order = this.get(orderId);
			int status = order.getStatus();
			//如果当前交易已完成，则不能再执行步骤
			if(status == OrderStatus.COMPLETED.ordinal()){
				throw new Exception("交易已完成，无法执行其他步骤");
			}
			if(status == OrderStatus.CANCEL.ordinal()){
				throw new Exception("交易已取消，无法执行其他步骤");
			}
			Integer step = order.getStepId();

			//若当前执行发货步骤，则修改订单的发货时间
			if(status == OrderStatus.DELIVERING.ordinal()){
				order.setDelivery_at(DateTool.now());
			}
			//获取下一步步骤,  若当前执行机织步骤，则不修改status,但修改step,执行后的状态为动态生产步骤
			if(status == OrderStatus.MACHINING.ordinal()){//若当前步骤是机织，则要获取动态生产步骤
				//获取下一步动态生产步骤
				OrderProduceStatus nextStep = orderProduceStatusService.getNext(order.getId(), step);
				if(nextStep == null){//如果获取不到下一个步骤，则跳到下一个status
					OrderStatus orderstatus = OrderStatusUtil.getNext(status);
					order.setStepId(null);
					order.setStep_state(null);
					order.setStatus(orderstatus.ordinal());
					order.setState(orderstatus.getName());
				}else{
					order.setStepId(nextStep.getId());
					order.setStep_state(nextStep.getName());
				}
			}
			else{//若不是机织，则status 直接+1
				OrderStatus orderstatus = OrderStatusUtil.getNext(status);
				order.setStepId(null);
				order.setStep_state(null);
				order.setStatus(orderstatus.ordinal());
				order.setState(orderstatus.getName());
			}
			
			
			// 更新订单表
			this.update(order, "id",
					"created_user,created_at,orderNumber", false);
			// 添加操作记录
			handle.setOrderId(orderId);
			if(order.getStepId()!=null){
				handle.setState(order.getStep_state());
				handle.setStatus(order.getStepId());
			}else{
				handle.setState(order.getState());
				handle.setStatus(order.getStatus());
			}
			
			orderHandleService.add(handle);
			return 1;
		} catch (Exception e) {
			throw e;
		}
	}
	
	@Transactional
	public int addNotification(ProductionNotification ProductionNotification) throws Exception{
		try{
			productionNotificationService.add(ProductionNotification);
			return 1;
		}catch(Exception e){
			throw e;
		}
	}
	
	
}
