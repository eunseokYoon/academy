const STAR_INDEXES = [1, 2, 3, 4, 5] as const;

/** 별 하나의 도형. viewBox 24x24, 채운 별이라 stroke가 아니라 fill을 쓴다. */
const STAR_PATH =
  "M12 2.5l2.95 5.98 6.6.96-4.78 4.66 1.13 6.58L12 17.77l-5.9 3.1 1.13-6.58L2.45 9.44l6.6-.96z";

function StarGlyph({ className }: { className: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path d={STAR_PATH} />
    </svg>
  );
}

interface StarHalfProps {
  side: "left" | "right";
  label: string;
  readOnly: boolean;
  onSelect: () => void;
}

/**
 * 별 한 칸의 좌/우 절반. 입력 모드는 button, 표시 모드는 span이다.
 * 표시 모드에 button을 두면 스크린리더가 눌러도 되는 것으로 읽는다.
 */
function StarHalf({ side, label, readOnly, onSelect }: StarHalfProps) {
  const position = side === "left" ? "left-0" : "right-0";
  if (readOnly) {
    return <span aria-hidden="true" className={`absolute inset-y-0 ${position} w-1/2`} />;
  }
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onSelect}
      className={`absolute inset-y-0 ${position} w-1/2`}
    />
  );
}

/**
 * 별 한 칸. 채워질 비율(0 / 0.5 / 1)만큼 amber 레이어를 왼쪽부터 클립해서
 * slate 바탕 별 위에 겹친다 — 반쪽 별 글리프를 따로 구하는 대신 쓰는 방식이다.
 */
function Star({
  index,
  value,
  readOnly,
  onSelect,
}: {
  index: number;
  value: number;
  readOnly: boolean;
  onSelect: (value: number) => void;
}) {
  const fill = Math.min(1, Math.max(0, value - (index - 1)));

  return (
    <div className="relative h-11 w-11 shrink-0">
      <StarGlyph className="absolute inset-0 h-11 w-11 text-slate-200" />
      <div className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
        <StarGlyph className="h-11 w-11 text-amber-400" />
      </div>
      <StarHalf
        side="left"
        label={`${index - 0.5}점`}
        readOnly={readOnly}
        onSelect={() => onSelect(index - 0.5)}
      />
      <StarHalf side="right" label={`${index}점`} readOnly={readOnly} onSelect={() => onSelect(index)} />
    </div>
  );
}

interface StarRatingInputProps {
  value: number;
  onChange: (value: number) => void;
  readOnly?: false;
}

interface StarRatingDisplayProps {
  value: number;
  onChange?: undefined;
  readOnly: true;
}

type StarRatingProps = StarRatingInputProps | StarRatingDisplayProps;

/**
 * 0.5 단위 별점. 별 하나를 좌/우 절반으로 나눠 탭한다.
 *
 * 별을 44px로 키운 이유가 있다. 360px에서 반쪽 터치 타깃이 22px면 너무 작아
 * 학생이 4.5를 누르려다 5.0을 누른다. 옆에 숫자를 같이 띄우는 것도 같은 이유다 —
 * 숫자가 없으면 자기가 뭘 골랐는지 확신하지 못한다.
 *
 * readOnly면 표시 전용이고 반개는 좌측 절반만 칠한다.
 */
export function StarRating(props: StarRatingProps) {
  const { value } = props;
  const readOnly = props.readOnly === true;
  const onSelect = props.readOnly ? () => {} : props.onChange;

  return (
    <div
      className="flex items-center gap-2"
      role={readOnly ? "img" : undefined}
      aria-label={readOnly ? `${value.toFixed(1)}점` : undefined}
    >
      <div className="flex">
        {STAR_INDEXES.map((index) => (
          <Star key={index} index={index} value={value} readOnly={readOnly} onSelect={onSelect} />
        ))}
      </div>
      <span aria-hidden={readOnly || undefined} className="tnum text-[15px] font-bold text-brand-900">
        {value.toFixed(1)}
      </span>
    </div>
  );
}
