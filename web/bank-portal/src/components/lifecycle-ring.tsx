import {
  ApplicationIcon,
  ApprovalIcon,
  CreditIcon,
  DisbursementIcon,
  NplIcon,
  RepaymentIcon,
} from "@/components/icons";
import { cn } from "@/lib/cn";

/**
 * The loan lifecycle, drawn as the flow it actually is.
 *
 * <p>Three things about the shape are deliberate and were wrong in the first
 * version of this diagram.
 *
 * <p><b>It is not a closed circle.</b> A loan enters at origination and leaves
 * at closure; nothing travels from recovery back round to origination. The
 * healthy path therefore runs origination to repayment and stops.
 *
 * <p><b>NPL is a branch, not a stage.</b> Only a loan that has missed two or
 * more instalments leaves repayment for recovery. Drawing it in line with the
 * others said that every loan ends up there, which is the opposite of the
 * truth. It is dashed, off-palette, labelled with its condition, and its
 * animation is off the edge for most of its cycle.
 *
 * <p><b>Work arrives from outside.</b> Applications reach origination from the
 * customer app, the public site and from staff raising one on a customer's
 * behalf. Those are the three inbound channels across the top.
 *
 * <p>Colours come from the active theme, set through {@code style} rather than
 * as {@code fill}/{@code stroke} attributes because SVG presentation attributes
 * are not parsed as CSS and so do not resolve {@code var()}. Motion lives in
 * globals.css and is disabled under prefers-reduced-motion.
 */
const CX = 460;
const CY = 450;
const RING = 185;
const LABEL_RING = 232;

/** Half-angle a 30px node subtends at the ring, so edges stop at its rim. */
const NODE_GAP = 11;

/** Polar to cartesian, with 0 degrees at three o'clock and y increasing down. */
function point(angle: number, radius: number) {
  const radians = (angle * Math.PI) / 180;
  return { x: CX + radius * Math.cos(radians), y: CY + radius * Math.sin(radians) };
}

/** Clockwise minor arc between two angles on the ring. */
function arc(fromAngle: number, toAngle: number) {
  const from = point(fromAngle + NODE_GAP, RING);
  const to = point(toAngle - NODE_GAP, RING);
  return `M ${from.x.toFixed(1)} ${from.y.toFixed(1)} A ${RING} ${RING} 0 0 1 ${to.x.toFixed(1)} ${to.y.toFixed(1)}`;
}

const STAGES = [
  { step: 1, name: "ORIGINATION", angle: -90, Icon: ApplicationIcon },
  { step: 2, name: "CREDIT", angle: -30, Icon: CreditIcon },
  { step: 3, name: "APPROVAL", angle: 30, Icon: ApprovalIcon },
  { step: 4, name: "DISBURSEMENT", angle: 90, Icon: DisbursementIcon },
  { step: 5, name: "REPAYMENT", angle: 150, Icon: RepaymentIcon },
  { step: 6, name: "NPL & RECOVERY", angle: 210, Icon: NplIcon },
] as const;

/** The path every performing loan takes, one edge per hand-off. */
const HEALTHY_EDGES = [
  { from: -90, to: -30 },
  { from: -30, to: 30 },
  { from: 30, to: 90 },
  { from: 90, to: 150 },
];

/** Where a loan enters the lifecycle from. */
const CHANNELS = [
  { label: "MOBILE APP", x: 175, path: "M 175 122 C 175 200, 300 224, 434 243" },
  { label: "WEBSITE", x: 460, path: "M 460 122 L 460 236" },
  { label: "BACK OFFICE", x: 745, path: "M 745 122 C 745 200, 620 224, 486 243" },
];

function labelAnchor(angle: number): "start" | "middle" | "end" {
  if (angle === -90 || angle === 90) return "middle";
  return Math.cos((angle * Math.PI) / 180) > 0 ? "start" : "end";
}

export function LifecycleRing({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 920 770"
      role="img"
      aria-label={
        "The loan lifecycle. Applications arrive at origination from the mobile app, "
        + "the website and the back office. A loan then moves through credit, approval, "
        + "disbursement and repayment. Only loans that miss two or more instalments "
        + "branch from repayment to NPL and recovery."
      }
      className={cn("h-full w-full", className)}
    >
      {/* ---- Inbound channels ------------------------------------------ */}
      <g style={{ color: "var(--viz-label)" }}>
        {CHANNELS.map(({ label, x, path }, index) => (
          <g key={label}>
            <rect
              x={x - 74}
              y={62}
              width={148}
              height={44}
              rx={22}
              style={{ fill: "var(--viz-node-fill)", stroke: "var(--viz-node)" }}
              strokeOpacity="0.5"
              strokeWidth="1.4"
            />
            <text
              x={x}
              y={90}
              textAnchor="middle"
              style={{ fill: "var(--viz-label)" }}
              fontSize="15"
              fontWeight="600"
              letterSpacing="1.4"
              fontFamily="var(--font-mono, ui-monospace), ui-monospace, monospace"
            >
              {label}
            </text>

            <path
              d={path}
              fill="none"
              style={{ stroke: "var(--viz-line)" }}
              strokeOpacity="0.25"
              strokeWidth="1.4"
            />
            <path
              className="dlp-flow"
              style={
                {
                  stroke: "var(--viz-node)",
                  "--dlp-delay": `${index * 0.9}s`,
                  "--dlp-duration": "3.6s",
                } as React.CSSProperties
              }
              d={path}
              pathLength={100}
              fill="none"
              strokeWidth="3.4"
              strokeLinecap="round"
            />
          </g>
        ))}
      </g>

      {/* ---- The ring the loan travels --------------------------------- */}
      <circle
        cx={CX}
        cy={CY}
        r={RING + 58}
        fill="none"
        style={{ stroke: "var(--viz-line)" }}
        strokeOpacity="0.1"
        strokeWidth="1"
        strokeDasharray="3 10"
      />

      {HEALTHY_EDGES.map((edge, index) => {
        const d = arc(edge.from, edge.to);
        const marker = point((edge.from + edge.to) / 2, RING);
        return (
          <g key={edge.from}>
            <path
              d={d}
              fill="none"
              style={{ stroke: "var(--viz-line)" }}
              strokeOpacity="0.4"
              strokeWidth="1.6"
            />
            <path
              className="dlp-flow"
              style={
                {
                  stroke: "var(--viz-node)",
                  "--dlp-delay": `${index * 1.1}s`,
                } as React.CSSProperties
              }
              d={d}
              pathLength={100}
              fill="none"
              strokeWidth="3.6"
              strokeLinecap="round"
            />
            <path
              d="M -5 -4.5 L 5.5 0 L -5 4.5 Z"
              transform={`translate(${marker.x} ${marker.y}) rotate(${(edge.from + edge.to) / 2 + 90})`}
              style={{ fill: "var(--viz-line)" }}
              fillOpacity="0.55"
            />
          </g>
        );
      })}

      {/* ---- The exception: repayment to NPL ---------------------------- */}
      <g>
        <path
          d={arc(150, 210)}
          fill="none"
          style={{ stroke: "var(--viz-warn, #f59e0b)" }}
          strokeOpacity="0.4"
          strokeWidth="1.6"
          strokeDasharray="6 7"
        />
        <path
          className="dlp-flow-conditional"
          style={{ stroke: "var(--viz-warn, #f59e0b)" } as React.CSSProperties}
          d={arc(150, 210)}
          pathLength={100}
          fill="none"
          strokeWidth="3.6"
          strokeLinecap="round"
        />
        <path
          d="M -5 -4.5 L 5.5 0 L -5 4.5 Z"
          transform={`translate(${point(180, RING).x} ${point(180, RING).y}) rotate(270)`}
          style={{ fill: "var(--viz-warn, #f59e0b)" }}
          fillOpacity="0.7"
        />

        {/* The condition, on the edge it governs. */}
        <rect
          x={point(180, 268).x - 88}
          y={point(180, 268).y - 17}
          width={176}
          height={34}
          rx={17}
          style={{ fill: "var(--viz-node-fill)", stroke: "var(--viz-warn, #f59e0b)" }}
          strokeOpacity="0.55"
          strokeWidth="1.3"
        />
        <text
          x={point(180, 268).x}
          y={point(180, 268).y + 5}
          textAnchor="middle"
          style={{ fill: "var(--viz-warn, #f59e0b)" }}
          fontSize="14"
          fontWeight="600"
          letterSpacing="0.8"
          fontFamily="var(--font-mono, ui-monospace), ui-monospace, monospace"
        >
          &#8805; 2 MISSED EMIs
        </text>
      </g>

      {/* ---- Core: the bank the file belongs to ------------------------- */}
      <g
        style={{ stroke: "var(--viz-emblem)" }}
        strokeOpacity="0.55"
        strokeWidth="2.4"
        strokeLinecap="round"
        fill="none"
      >
        <path d={`M${CX} ${CY - 40} ${CX + 42} ${CY - 12}H${CX - 42}L${CX} ${CY - 40}Z`} />
        <path d={`M${CX - 26} ${CY + 2}v44M${CX} ${CY + 2}v44M${CX + 26} ${CY + 2}v44`} />
        <path d={`M${CX - 42} ${CY + 58}h84`} />
      </g>

      {/* ---- The six states -------------------------------------------- */}
      {STAGES.map(({ step, name, angle, Icon }, index) => {
        const node = point(angle, RING);
        const label = angle === -90 ? { x: CX, y: CY - 128 } : point(angle, LABEL_RING);
        const isException = step === 6;
        const accent = isException ? "var(--viz-warn, #f59e0b)" : "var(--viz-node)";

        return (
          <g key={name}>
            {/* Halo, pulsing as the flow reaches this state. */}
            <circle
              className="dlp-halo"
              style={
                { fill: accent, "--dlp-delay": `${index * 1.1}s` } as React.CSSProperties
              }
              cx={node.x}
              cy={node.y}
              r="42"
            />
            <circle
              cx={node.x}
              cy={node.y}
              r="30"
              style={{ fill: "var(--viz-node-fill)", stroke: accent }}
              strokeOpacity="0.85"
              strokeWidth="1.8"
            />
            <g transform={`translate(${node.x - 12} ${node.y - 13})`} style={{ color: accent }}>
              {/* Nested SVG, so it needs an explicit size in user units. */}
              <Icon width={24} height={24} />
            </g>

            <circle
              cx={node.x + 21}
              cy={node.y + 21}
              r="10.5"
              style={{ fill: accent }}
              fillOpacity="0.9"
            />
            <text
              x={node.x + 21}
              y={node.y + 25}
              textAnchor="middle"
              style={{ fill: "var(--viz-node-fill)" }}
              fontSize="12"
              fontWeight="700"
            >
              {step}
            </text>

            <text
              x={label.x}
              y={label.y + 5}
              textAnchor={angle === -90 ? "middle" : labelAnchor(angle)}
              style={{ fill: isException ? "var(--viz-warn, #f59e0b)" : "var(--viz-label)" }}
              fontSize="16"
              fontWeight="600"
              letterSpacing="1.6"
              fontFamily="var(--font-mono, ui-monospace), ui-monospace, monospace"
            >
              {name}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
