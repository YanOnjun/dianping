package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalIDGenerator;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 *
 * @author zx065
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private GlobalIDGenerator generator;

    @Override
    public Result createOrder(Long voucherId) {
        // 查询优惠券id
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        checkVoucher(voucher);
        Long id = UserHolder.getUser().getId();
        // 獲取代理對象對執行方法加鎖 string.intern 保證字符串内容相同加上鎖
        synchronized (id.toString().intern()) {
            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
            return o.realCreateOrder(voucherId);
        }
    }

    @Override
    @Transactional
    public Result realCreateOrder(Long voucherId) {
        // 减少库存 乐观锁 stock >0 解决超卖
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!update) {
            throw new BusinessException("优惠券已经被抢完了");
        }
        UserDTO user = UserHolder.getUser();
        int count = query().eq("user_id", user.getId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new BusinessException("只能购买一次");
        }
        // 创建订单
        VoucherOrder voucherOrder = createVoucherOrder(voucherId, user.getId());
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }

    private void checkVoucher(SeckillVoucher voucher) {
        if (Objects.isNull(voucher)) {
            throw new BusinessException("优惠券不存在");
        }
        // 判断秒杀是否开始了
        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
            throw new BusinessException("秒杀还未开始");
        }
        // 判断秒杀是否结束了
        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
            throw new BusinessException("秒杀已经结束了");
        }
        // 判断库存是否充足
        if (voucher.getStock() <= 0) {
            throw new BusinessException("优惠券已经被抢完了");
        }
    }

    private VoucherOrder createVoucherOrder(Long voucherId,Long userId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(generator.generate("VoucherOrder"));
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        return order;
    }
}
