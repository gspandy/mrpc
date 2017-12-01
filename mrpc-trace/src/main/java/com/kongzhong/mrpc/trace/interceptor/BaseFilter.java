package com.kongzhong.mrpc.trace.interceptor;

import com.kongzhong.basic.zipkin.TraceContext;
import com.kongzhong.basic.zipkin.agent.AbstractAgent;
import com.kongzhong.basic.zipkin.agent.KafkaAgent;
import com.kongzhong.basic.zipkin.util.ServerInfo;
import com.kongzhong.mrpc.serialize.jackson.JacksonSerialize;
import com.kongzhong.mrpc.trace.TraceConstants;
import com.kongzhong.mrpc.trace.config.TraceClientAutoConfigure;
import com.kongzhong.mrpc.utils.Ids;
import com.kongzhong.mrpc.utils.TimeUtils;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;

/**
 * @author biezhi
 * @date 2017/12/1
 */
@Slf4j
public class BaseFilter {

    private AbstractAgent            agent;
    private TraceClientAutoConfigure clientAutoConfigure;

    public BaseFilter(TraceClientAutoConfigure clientAutoConfigure) {
        try {
            this.clientAutoConfigure = clientAutoConfigure;
            this.agent = new KafkaAgent(clientAutoConfigure.getUrl(), clientAutoConfigure.getTopic());
        } catch (Exception e) {
            log.error("初始化Trace客户端失败", e);
        }
    }

    public boolean enabled() {
        return clientAutoConfigure.getEnable();
    }

    public void startTrace(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // start root span
        Span rootSpan = startTrace(request, uri);
        TraceContext.setRootSpan(rootSpan);
        if (log.isDebugEnabled()) {
            log.debug("Trace request url: {}", uri);
            TraceContext.print();
        }
        // prepare trace context
        TraceContext.start();
        TraceContext.setTraceId(rootSpan.getTrace_id());
        TraceContext.setSpanId(rootSpan.getId());
        TraceContext.addSpan(rootSpan);
    }

    private Span startTrace(HttpServletRequest req, String point) {
        Span   apiSpan = new Span();

        // span basic data
        Long traceId = TraceContext.getTraceId();
        if (null == traceId) {
            traceId = Ids.get();
        }

        long timestamp = TimeUtils.currentMicros();

        apiSpan.setId(traceId);
        apiSpan.setTrace_id(traceId);
        apiSpan.setName(point);
        apiSpan.setTimestamp(timestamp);

        // sr annotation
        apiSpan.addToAnnotations(
                Annotation.create(timestamp, TraceConstants.ANNO_SR,
                        Endpoint.create(System.getenv("APPID"), ServerInfo.IP4, req.getLocalPort())));

        // app name
        apiSpan.addToBinary_annotations(BinaryAnnotation.create(
                "name", clientAutoConfigure.getName(), null
        ));

        // app owner
        apiSpan.addToBinary_annotations(BinaryAnnotation.create(
                "负责人", clientAutoConfigure.getOwner(), null
        ));
        return apiSpan;
    }

    public void endTrace(HttpServletRequest request) {
        // end root span
        Span rootSpan = TraceContext.getRootSpan();
        if (null != rootSpan) {
            long times = TimeUtils.currentMicros() - rootSpan.getTimestamp();
            endTrace(request, rootSpan, times);
        }
    }

    private void endTrace(HttpServletRequest req, Span span, long times) {
        // ss annotation
        span.addToAnnotations(
                Annotation.create(TimeUtils.currentMicros(), TraceConstants.ANNO_SS,
                        Endpoint.create(System.getenv("APPID"), ServerInfo.IP4, req.getLocalPort())));

        span.setDuration(times);

        // send trace spans
        try {
            agent.send(TraceContext.getSpans());
            if (log.isDebugEnabled()) {
                log.debug("Send trace data {}.", JacksonSerialize.toJSONString(TraceContext.getSpans()));
            }
        } catch (Exception e) {
            log.error("发送到Trace失败", e);
        }
        // clear trace context
        TraceContext.clear();
        if (log.isDebugEnabled()) {
            log.debug("Trace clear.");
            TraceContext.print();
        }
    }

}
