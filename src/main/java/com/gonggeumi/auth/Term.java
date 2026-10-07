package com.gonggeumi.auth;

import java.time.Instant;

/** API 읽기 모델: 식별자는 JSON 정밀도 손실을 막기 위해 문자열이다. */
public record Term(String id, String termType, String versionName, String title,
                   String content, boolean isRequired, Instant publishedAt) {}
