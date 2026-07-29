import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { get } from "../api/client";

export interface Child {
  studentId: number;
  name: string;
}

interface SelectedChildValue {
  children: Child[];
  loading: boolean;
  selectedStudentId: number | null;
  setSelectedStudentId: (studentId: number) => void;
}

const STORAGE_KEY = "academy.selectedStudentId";
const SelectedChildContext = createContext<SelectedChildValue | null>(null);

/**
 * 학부모 화면 대부분이 studentId를 필요로 한다. 화면마다 드롭다운을 따로 두지 않고
 * 여기 한 곳에서 관리한다. 선택값은 sessionStorage에 남겨 새로고침을 견딘다.
 */
export function SelectedChildProvider({ children }: { children: ReactNode }) {
  const { data, isPending } = useQuery({
    queryKey: ["parent", "children"],
    queryFn: () => get<Child[]>("/parent/children"),
  });

  const [selectedStudentId, setSelected] = useState<number | null>(() => {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    return saved ? Number(saved) : null;
  });

  const list = useMemo(() => data ?? [], [data]);

  // 저장된 선택이 없거나 더 이상 내 자녀가 아니면 첫 아이로 맞춘다
  useEffect(() => {
    if (list.length === 0) return;
    if (!list.some((child) => child.studentId === selectedStudentId)) {
      setSelected(list[0].studentId);
    }
  }, [list, selectedStudentId]);

  useEffect(() => {
    if (selectedStudentId !== null) {
      sessionStorage.setItem(STORAGE_KEY, String(selectedStudentId));
    }
  }, [selectedStudentId]);

  const value = useMemo(
    () => ({
      children: list,
      loading: isPending,
      selectedStudentId,
      setSelectedStudentId: setSelected,
    }),
    [list, isPending, selectedStudentId],
  );

  return (
    <SelectedChildContext.Provider value={value}>{children}</SelectedChildContext.Provider>
  );
}

export function useSelectedChild(): SelectedChildValue {
  const context = useContext(SelectedChildContext);
  if (!context) throw new Error("useSelectedChild는 SelectedChildProvider 안에서만 쓸 수 있습니다.");
  return context;
}
