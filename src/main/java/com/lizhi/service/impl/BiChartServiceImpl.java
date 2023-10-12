package com.lizhi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lizhi.common.BusinessException;
import com.lizhi.common.ErrorCode;
import com.lizhi.constant.BiConstant;
import com.lizhi.manage.AiManage;
import com.lizhi.mapper.BiChartMapper;
import com.lizhi.model.dto.chart.ChartAddRequest;
import com.lizhi.model.entity.BiChart;
import com.lizhi.service.BiChartService;
import com.lizhi.utils.ExcelUtils;
import com.lizhi.utils.ThrowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
* @author <a href="https://github.com/lizhe-0423">lizhi</a>
* @description 针对表【bi_chart(BI图表表)】的数据库操作Service实现
* @createDate 2023-10-11 16:05:20
*/
@Service
@Slf4j
public class BiChartServiceImpl extends ServiceImpl<BiChartMapper, BiChart>
    implements BiChartService{

    @Resource
    AiManage aiManage;

    @Override
    public BiChart saveChart(ChartAddRequest chartAddRequest, MultipartFile multipartFile) {
        ThrowUtil.throwIf(CharSequenceUtil.isBlank(chartAddRequest.getChartGoal()),ErrorCode.NOT_FOUND_ERROR);
        ThrowUtil.throwIf(CharSequenceUtil.isBlank(chartAddRequest.getChartType()),ErrorCode.NOT_FOUND_ERROR);
        ThrowUtil.throwIf(CharSequenceUtil.isBlank(chartAddRequest.getChartName()),ErrorCode.NOT_FOUND_ERROR);
        String excelToCsv = ExcelUtils.excelToCsv(multipartFile);
        String message = getChartMessage(chartAddRequest.getChartGoal(), chartAddRequest.getChartType(), excelToCsv);
        BiChart chart = new BiChart();
        BeanUtil.copyProperties(chartAddRequest,chart);
        chart.setChartText(excelToCsv);
        chart.setChartStatus("waiting");
        this.save(chart);
        CompletableFuture.runAsync(()->{
            chart.setChartStatus("running");
            boolean b = this.updateById(chart);
            if(!b){
                handleChartUpdateError(chart,"图表更新失败");
                return;
            }
            String doChat = aiManage.doChat(BiConstant.CHART_ID, message);
            BiChart receivedChartMessage = receiveChartMessage(doChat, chart);
            this.updateById(receivedChartMessage);

        });
        return null;
    }

    @Override
    public String getChartMessage(String userGoal, String chartType,String excelToCsv) {
        return "分析需求:"+"\n"+userGoal+"请使用"+chartType+"\n"+"原始数据"+"\n"+excelToCsv;
    }

    @Override
    public BiChart receiveChartMessage(String doChart, BiChart biChart) {
        String[] splits = doChart.split("【【【【【");
        int doChatLen = 3;
        if (splits.length< doChatLen){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI返回错误");
        }
        //对AI生成的数据进行分割
        String genChart = splits[1];
        String genResult = splits[2];
        // 将状态改为成功
        BiChart updateChartAfter = new BiChart();
        updateChartAfter.setChartStatus("succeed");
        updateChartAfter.setChartGen(genChart);
        updateChartAfter.setChartResult(genResult);
        return updateChartAfter;
    }

    @Override
    public void handleChartUpdateError(BiChart chart, String execMessage) {
        BiChart updateChart = new BiChart();
        updateChart.setChartStatus("failed");
        updateChart.setChartText("failed");
        boolean b2 = updateById(updateChart);
        if(!b2){
            log.error("更新图表失败状态失败{}{}",chart,execMessage);
        }
    }
}




