import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { FormError } from "../../shared/components/FormError";
import { PageTitle, TintBlock } from "../../shared/components/Section";
import { CATEGORY_LABELS, formatBytes } from "../../shared/material/types";
import type { MaterialCategory } from "../../shared/material/types";
import { formatWeek } from "../teacher/format";
import { getMaterialDownloadUrl, listMyMaterials } from "./api";

const FILTERS: { value: MaterialCategory | ""; label: string }[] = [
  { value: "", label: "전체" },
  { value: "LESSON", label: "수업자료" },
  { value: "TEXTBOOK", label: "교재" },
  { value: "PAST_EXAM", label: "기출" },
  { value: "ETC", label: "기타" },
];

/**
 * S-8 수업 자료실. 학생 전용이다.
 *
 * <p>최신순으로 내려온다 — 학생은 "이번 주 자료"를 맨 위에서 찾는다. 다시 정렬하지 마라.
 *
 * <p>미리보기(PDF 뷰어)를 붙이지 마라. 다운로드만이다.
 */
export default function StudentMaterialPage() {
  const [category, setCategory] = useState<MaterialCategory | "">("");

  const materials = useQuery({
    queryKey: ["student", "materials", category],
    queryFn: () => listMyMaterials(category === "" ? {} : { category }),
  });

  /**
   * 다운로드 URL은 유효기간이 5분이라 <b>누른 순간 받아서 바로 이동</b>한다.
   * 목록에 미리 담아두면 화면을 열어둔 사이 전부 만료된다.
   */
  const download = useMutation({
    mutationFn: getMaterialDownloadUrl,
    onSuccess: (data) => {
      window.location.href = data.downloadUrl;
    },
  });

  const items = materials.data?.items ?? [];

  return (
    <div className="space-y-3">
      <PageTitle>수업 자료실</PageTitle>

      {/* 고른 칩은 남색으로 채운다. 검정으로 두면 앱바 남색과 따로 논다 */}
      <div className="no-scrollbar -mx-4 flex gap-1.5 overflow-x-auto px-4 pb-0.5">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setCategory(filter.value)}
            className={`shrink-0 rounded-full px-3.5 py-1.5 text-[13px] font-semibold
                        transition-colors ${
                          category === filter.value
                            ? "bg-brand-900 text-white"
                            : "bg-white text-slate-600 shadow-card"
                        }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {download.isError && <FormError message={errorMessage(download.error)} />}

      {materials.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : items.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">받은 자료가 없습니다.</p>
        </TintBlock>
      ) : (
        <TintBlock tone="neutral">
          {items.map((material) => (
            <button
              key={material.materialId}
              type="button"
              onClick={() => download.mutate(material.materialId)}
              disabled={download.isPending}
              className="flex w-full items-center justify-between gap-2 px-3.5 py-3 text-left
                         transition-colors active:bg-slate-50 disabled:opacity-60"
            >
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1.5">
                  <Badge tone="neutral">{CATEGORY_LABELS[material.category]}</Badge>
                  <span className="truncate text-[14px] font-semibold text-brand-900">
                    {material.title}
                  </span>
                </span>
                <span className="tnum mt-0.5 block truncate text-[11.5px] text-slate-500">
                  {formatWeek(material.year, material.month, material.week)} · {material.fileName}
                  {material.bytes !== null && ` · ${formatBytes(material.bytes)}`}
                </span>
              </span>
              <span className="shrink-0 rounded-lg border border-brand-200 bg-white px-2 py-1
                               text-[11px] font-bold text-brand-700">
                받기
              </span>
            </button>
          ))}
        </TintBlock>
      )}
    </div>
  );
}
