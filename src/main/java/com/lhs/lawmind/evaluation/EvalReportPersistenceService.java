package com.lhs.lawmind.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhs.lawmind.entity.EvalReportRecord;
import com.lhs.lawmind.mapper.EvalReportRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 评估报告持久化服务
 * 将 Golden Dataset 评估结果写入 MySQL，支持历史趋势对比
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalReportPersistenceService {

    private final EvalReportRecordMapper mapper;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 保存评估报告到数据库
     */
    public EvalReportRecord saveReport(EvaluationReport report, String datasetPath) {
        EvalReportRecord record = new EvalReportRecord();
        record.setRunId(UUID.randomUUID().toString());
        record.setDatasetPath(datasetPath);
        record.setDatasetVersion(1);
        record.setTotalCases(report.getTotalCases());
        record.setPassedCases(report.getPassedCases());
        record.setFailedCases(report.getFailedCases());

        if (report.getDimensionAverages() != null) {
            var avgs = report.getDimensionAverages();
            record.setAvgKeywordRecall(bd(avgs.get("keywordRecall")));
            record.setAvgSourceMatch(bd(avgs.get("sourceMatch")));
            record.setAvgLawTypeMatch(bd(avgs.get("lawTypeMatch")));
            record.setAvgAnswerLength(bd(avgs.get("answerMinLength")));
            record.setAvgTotalScore(bd(avgs.get("totalScore")));
            record.setAvgFaithfulness(bd(avgs.get("faithfulness")));
            record.setAvgAnswerRelevance(bd(avgs.get("answerRelevance")));
        }

        try {
            record.setReportJson(jsonMapper.writeValueAsString(report));
        } catch (Exception e) {
            log.warn("评估报告JSON序列化失败: {}", e.getMessage());
        }

        mapper.insert(record);
        log.info("[评估持久化] 报告已保存: runId={} total={} passed={} failed={}",
                record.getRunId(), record.getTotalCases(), record.getPassedCases(), record.getFailedCases());
        return record;
    }

    private static BigDecimal bd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        try { return new BigDecimal(v.toString()).setScale(4, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
