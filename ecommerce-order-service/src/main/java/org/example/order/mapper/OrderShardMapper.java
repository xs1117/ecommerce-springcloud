package org.example.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.order.domain.OrderDetailView;
import org.example.order.domain.OrderInfo;
import org.example.order.domain.OrderItem;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderShardMapper {

    int insertOrder(@Param("table") String table, @Param("order") OrderInfo order);

    int insertOrderItem(@Param("table") String table, @Param("item") OrderItem item);

    int updateOrderStatus(@Param("table") String table,
                          @Param("orderNo") String orderNo,
                          @Param("status") String status);

    @Update("UPDATE ${table} SET status = #{status}, updated_at = NOW() WHERE order_no = #{orderNo} AND status = #{expectedStatus}")
    int updateOrderStatusIfCurrent(@Param("table") String table,
                                   @Param("orderNo") String orderNo,
                                   @Param("status") String status,
                                   @Param("expectedStatus") String expectedStatus);

    OrderInfo findByOrderNo(@Param("table") String table, @Param("orderNo") String orderNo);

    List<OrderItem> findItemsByOrderNo(@Param("table") String table, @Param("orderNo") String orderNo);

    List<OrderDetailView> queryComplex(@Param("userId") Long userId,
                                       @Param("status") String status,
                                       @Param("limit") Integer limit);

    List<Map<String, Object>> queryMerchantOrders(@Param("storeId") Long storeId,
                                                  @Param("status") String status,
                                                  @Param("limit") Integer limit);

    Map<String, Object> queryMerchantStats(@Param("storeId") Long storeId);

    Map<String, Object> queryUserNotificationSummary(@Param("userId") Long userId);

    List<OrderInfo> findTimeoutOrders(@Param("table") String table, @Param("minutes") int minutes);
}

