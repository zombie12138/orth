package com.xxl.job.core.util.deprecated;

/**
 * Deprecated sharding context utilities for distributed job execution.
 *
 * <p>This utility class provided thread-local storage for job sharding information (shard index and
 * total shards), allowing job handlers to access their assigned shard parameters during execution.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use
 *     com.xxl.job.core.context.XxlJobHelper.getShardIndex() and XxlJobHelper.getShardTotal()
 *     instead, which provide the same functionality through the unified job context API.
 * @author xuxueli 2017-07-25 21:26:38
 */
@Deprecated
public class ShardingUtil {

    private static InheritableThreadLocal<ShardingVO> contextHolder =
            new InheritableThreadLocal<ShardingVO>();

    public static class ShardingVO {

        private int index; // sharding index
        private int total; // sharding total

        public ShardingVO(int index, int total) {
            this.index = index;
            this.total = total;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public static void setShardingVo(ShardingVO shardingVo) {
        contextHolder.set(shardingVo);
    }

    public static ShardingVO getShardingVo() {
        return contextHolder.get();
    }
}
