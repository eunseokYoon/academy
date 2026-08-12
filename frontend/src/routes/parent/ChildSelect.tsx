import { useSelectedChild } from "../../shared/auth/SelectedChildContext";

/**
 * 하위 화면 제목 줄의 자녀 선택. <b>PageTitle의 action 자리에만 넣는다</b> —
 * 그 자리가 남색 띠 안이라 흰 계열로 그린다. 회색 테두리 셀렉트를 그대로 두면
 * 남색 위에서 안 보인다.
 *
 * <p>자녀가 하나면 아무것도 그리지 않는다. 고를 게 없는 선택지는 화면만 어지럽힌다.
 *
 * <p>네 화면(P-2·P-3·P-4·P-6)이 같은 것을 각자 그리고 있었다. 하나로 묶어야
 * 자녀를 바꿨을 때 어느 화면에서든 같은 모양이 된다.
 */
export function ChildSelect() {
  const { children, selectedStudentId, setSelectedStudentId } = useSelectedChild();

  if (children.length <= 1) return null;

  return (
    <select
      value={selectedStudentId ?? ""}
      onChange={(e) => setSelectedStudentId(Number(e.target.value))}
      aria-label="자녀 선택"
      className="shrink-0 rounded-lg border border-white/25 bg-white/10 px-2 py-1.5
                 text-[13px] font-medium text-white outline-none
                 transition-colors focus:border-white/45"
    >
      {children.map((child) => (
        /* 옵션 목록은 OS가 그린다 — 글자색을 되돌려 놔야 흰 배경 위에서 보인다 */
        <option key={child.studentId} value={child.studentId} className="text-brand-900">
          {child.name}
        </option>
      ))}
    </select>
  );
}
