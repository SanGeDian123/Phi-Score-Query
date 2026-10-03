/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { Theme } from './Theme';
import type { UnifiedSaveRequest } from './UnifiedSaveRequest';
import type { UserScoreItem } from './UserScoreItem';
/**
 * 用户自定义 BN 渲染请求（未验证成绩）
 */
export type RenderUserBnRequest = (UnifiedSaveRequest & {
    /**
     * 是否启用自定义 B30/P30 规则。
     */
    custom?: boolean;
    /**
     * 自定义榜单类型。
     */
    ranking?: 'b30' | 'p30';
    /**
     * 可选昵称（未提供时可从 users/me 获取）
     */
    nickname?: string | null;
    /**
     * 成绩列表
     */
    scores: Array<UserScoreItem>;
    /**
     * 主题（默认 black）
     */
    theme?: Theme;
    /**
     * 解除水印的口令（匹配配置或动态口令时，显式/隐式水印均关闭）
     */
    unlockPassword?: string | null;
    /**
     * 发起请求的 APP 版本。
     */
    appVersion?: string | null;
});

