import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { FormError } from "../../shared/components/FormError";
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
      <h2 className="text-lg font-semibold text-slate-900">수업 자료실</h2>

      <div className="flex gap-1 overflow-x-auto">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setCategory(filter.value)}
            className={`shrink-0 rounded-full px-3 py-1.5 text-sm ${
              category === filter.value
                ? "bg-slate-900 text-white"
                : "bg-white text-slate-600 shadow-sm"
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
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          받은 자료가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {items.map((material) => (
            <li key={material.materialId}>
              <button
                type="button"
                onClick={() => download.mutate(material.materialId)}
                disabled={download.isPending}
                className="flex w-full items-center justify-between gap-2 rounded-xl bg-white p-3
                           text-left shadow-sm disabled:opacity-60"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <Badge tone="neutral">{CATEGORY_LABELS[material.category]}</Badge>
                    <span className="truncate text-sm font-medium text-slate-900">
                      {material.title}
                    </span>
                  </span>
                  <span className="mt-0.5 block truncate text-xs text-slate-500">
                    {formatWeek(material.year, material.month, material.week)} ·{" "}
                    {material.fileName}
                    {material.bytes !== null && ` · ${formatBytes(material.bytes)}`}
                  </span>
                </span>
                <span className="shrink-0 text-xs text-slate-500 underline">받기</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
