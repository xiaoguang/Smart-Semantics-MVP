package example.depot;

import org.apache.ibatis.annotations.Mapper;

@Mapper
interface DepotHeadMapper {
  int updateByExampleSelective(DepotHead record, DepotHeadExample example);
}
