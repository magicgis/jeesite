package com.thinkgem.jeesite.api.service;

import com.thinkgem.jeesite.api.entity.handler.CabinerDrawerHandler;
import com.thinkgem.jeesite.api.entity.req.PreOrderReq;
import com.thinkgem.jeesite.api.entity.res.PlatformRes;
import com.thinkgem.jeesite.api.enums.ResCodeMsgType;
import com.thinkgem.jeesite.common.utils.StringUtils;
import com.thinkgem.jeesite.modules.manager.cabinet.dao.DrawerDao;
import com.thinkgem.jeesite.modules.manager.cabinetproductrelaction.dao.CabinetProductRelactionDao;
import com.thinkgem.jeesite.modules.manager.cabinetproductrelaction.entity.CabinetProductRelaction;
import com.thinkgem.jeesite.modules.manager.orders.dao.OrdersDao;
import com.thinkgem.jeesite.modules.manager.orders.entity.Orders;
import com.thinkgem.jeesite.modules.manager.orders.service.OrdersService;
import com.thinkgem.jeesite.modules.manager.product.entity.Product;
import com.thinkgem.jeesite.modules.manager.product.service.ProductService;
import com.thinkgem.jeesite.util.TenpayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 给外部提供的订单接口
 * Created by yangtao on 2017/9/29.
 */
@Service
public class OrderService {


    @Autowired
    private WechatPayService wechatPayService;
    @Autowired
    private OrdersService ordersService;
    @Autowired
    private ProductService productService;
    @Autowired
    private DrawerDao drawerDao;
    @Autowired
    private CabinetProductRelactionDao cabinetProductRelactionDao;
    @Autowired
    private OrdersDao ordersDao;


    /**
     * @param products
     * @param paymentStatus 支付类型: 0,微信扫码支付 1,微信公众号支付 2,支付宝
     * @param tradeType
     * @param repackgeId    用户红包表的id
     * @return
     */
    public PlatformRes<String> preorder(List<PreOrderReq> products, Integer paymentStatus, String tradeType, String repackgeId) {


        //参数验证
        if (products == null || products.isEmpty())
            return PlatformRes.error(ResCodeMsgType.PARAMS_NOT_EMPTY);
        //订单号
        String orderNo = TenpayUtil.getCurrTime();

        //商品id
        String productIds = "";
        //支付总金额
        Integer productTotalPrice = 0;

        for (PreOrderReq productReq : products) {

            //如果验证通过(区域,商品信息填充)
            PlatformRes<String> validResult = validOrderInfo(productReq, productIds, productTotalPrice);

            if (!validResult.getCode().equals("0"))
                return validResult;
        }


        //这里要创建订单,订单状态为0,等到回调通过以后更改状态,微信预约下单生成二维码
        PlatformRes<String> wechatPayResult = null;
        if (paymentStatus == 0) {
            wechatPayResult = wechatPayService.unifiedorder(orderNo, productIds, productTotalPrice, tradeType);
            //预支付id成功,生成订单
            if (!wechatPayResult.getCode().equals("0")) {
                return wechatPayResult;
            }

        } else if (paymentStatus == 1) { //公众号支付

        } else {       //支付宝扫码付

        }

        //------全部验证通过保存订单和订单明细--------------------------------------
        ordersService.submitForOrder(orderNo, paymentStatus, products, productTotalPrice, repackgeId);
        return wechatPayResult;


    }


    private PlatformRes<String> validOrderInfo(PreOrderReq productReq, String productIds, Integer productTotalPrice) {
        if (StringUtils.isBlank(productReq.getCabinetNo())) {
            return PlatformRes.error("传入参数柜子编号有错误!");
        }

        if (StringUtils.isBlank(productReq.getDrawerNo())) {
            return PlatformRes.error("传入参数抽屉编号有为空的");
        }


        if (StringUtils.isBlank(productReq.getProductId())) {
            return PlatformRes.error("传入参数商品ID为空!");
        }

        Product product = productService.get(productReq.getProductId());

        if (product == null)
            return PlatformRes.error(ResCodeMsgType.PRODUCT_NOT_EXISTS);
        //商品价格
        productReq.setGetProductActualPrice(product.getProductActualPrice());
        //拼接商品id和设置商品金额
        productIds = product.getId() + "," + productIds;
        productTotalPrice = product.getProductActualPrice() + productTotalPrice;

        //商品下架判断
        if (product.getProductStatus().equals("0"))
            return PlatformRes.error(ResCodeMsgType.PRODUCT_NOT_USE);

        //判断柜子抽屉是否存在
        CabinerDrawerHandler cabinerDrawerHandler = drawerDao.findCabinetAndDrawerInfo(productReq.getCabinetNo(), productReq.getDrawerNo());
        if (cabinerDrawerHandler == null)
            return PlatformRes.error(ResCodeMsgType.CABINET_DRAWER_EXISTS);

        //柜子是否可用
        if (!cabinerDrawerHandler.getDrawerStatus().equals("1"))
            return PlatformRes.error(ResCodeMsgType.DRAWER_NOT_ACTION);

        //柜子是否放餐,1.已放餐
        if (!cabinerDrawerHandler.getDrawerStatus().equals("1"))
            return PlatformRes.error(ResCodeMsgType.DRAWER_HAVING_NOT_FOOD);


        //设置区域信息给后面订单用,节省查询数据库次数
        productReq.setAreaId(cabinerDrawerHandler.getAreaId());
        productReq.setAreaName(cabinerDrawerHandler.getAreaName());
        productReq.setProductName(product.getProductName());
        productReq.setProductNum(1);
        //判断当前柜子是否可以放当前的商品
        CabinetProductRelaction cabinetProductRelaction = cabinetProductRelactionDao.findByDrawerNoAndProductId(productReq.getDrawerNo(), productReq.getProductId());
        if (cabinetProductRelaction == null)
            return PlatformRes.error(ResCodeMsgType.DRAWER_NOT_PUT_PRODUCT);


        return PlatformRes.success(null);
    }

    public PlatformRes<Orders> queryOrder(String orderNo) {
        Orders orders = ordersDao.getOrdersByOrderNo(orderNo);
        if (orders == null)
            return PlatformRes.error(ResCodeMsgType.ORDERS_NOT_EXISTS);
        /**
         *
         */
        if (StringUtils.isBlank(orders.getWechatTradeNo())) {
            //支付宝订单查询
            return null;
        } else {
            //订单号
            PlatformRes<String> result = wechatPayService.orderQuery(orderNo);
            if (result.getCode().equals("0")) {
                return PlatformRes.success(orders);
            } else {
                //订单号出错
                return PlatformRes.error(result.getCode(), result.getData(), orders);
            }
        }
    }


    /**
     * 退单,微信和支付宝
     *
     * @param orderNo
     * @return
     */
    public PlatformRes<String> refundOrder(String orderNo) {
        Orders orders = ordersDao.getOrdersByOrderNo(orderNo);
        if (orders == null)
            return PlatformRes.error(ResCodeMsgType.ORDERS_NOT_EXISTS);
        if (StringUtils.isBlank(orders.getWechatTradeNo())) {
            //支付宝订单查询
            return null;
        } else {
            if (orders.getRefundStatus().equals("0") || orders.getRefundStatus().equals("2")) {
                String refundOrderNo = StringUtils.isBlank(orders.getRefundNo()) ? TenpayUtil.getCurrTime() : orders.getRefundNo();
                PlatformRes<String> result = wechatPayService.wechatRefundFee(orderNo, refundOrderNo, orders.getActualPayMoney(), orders.getActualPayMoney());
                orders.setRefundNo(refundOrderNo);
                if (result.getCode().equals("0")) {
                    //退款成功
                    orders.setRefundStatus(1);
                    ordersDao.update(orders);
                    return PlatformRes.success("退款成功");
                } else {
                    //退款失败
                    orders.setRefundStatus(2);
                    ordersDao.update(orders);
                    return PlatformRes.error(ResCodeMsgType.REFUND_ERROR);
                }


            } else
                return PlatformRes.error(ResCodeMsgType.REFUND_ORDERS_NOT_EXISTS);

        }

    }

    /**
     * 查詢退款訂單,微信和支付宝
     *
     * @param orderNo
     * @return
     */
    public PlatformRes<String> queryRefundOrder(String orderNo) {
        Orders orders = ordersDao.getOrdersByOrderNo(orderNo);
        try {
            if (orders == null)
                return PlatformRes.error(ResCodeMsgType.ORDERS_NOT_EXISTS);
            if (StringUtils.isBlank(orders.getWechatTradeNo())) {
                //支付宝订单查询
                return null;
            } else {
                return wechatPayService.queryRefundOrder(orderNo);
            }
        } catch (Exception e) {
            return PlatformRes.error(e.getMessage());
        }

    }

}
