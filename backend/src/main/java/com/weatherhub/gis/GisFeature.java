package com.weatherhub.gis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("gis_feature")
public class GisFeature {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    private String geojson;

    private String properties;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String geometryJson;
}
