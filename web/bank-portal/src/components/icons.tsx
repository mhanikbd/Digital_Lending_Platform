/**
 * The handful of icons this portal needs, drawn inline.
 *
 * An icon package would be a dependency carrying hundreds of glyphs to render
 * nine, so these are hand-written on a shared 24px stroke grid instead.
 */
type IconProps = { className?: string; width?: number; height?: number };

function Icon({ className, width, height, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={width}
      height={height}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      {children}
    </svg>
  );
}

/** Origination: an application being filled in. */
export function ApplicationIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z" />
      <path d="M14 3v5h5" />
      <path d="M9 13h5M9 17h3" />
    </Icon>
  );
}

/** Credit and approval: a decision that has been checked. */
export function ApprovalIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 3 4 6v6c0 4.5 3.2 8 8 9 4.8-1 8-4.5 8-9V6l-8-3Z" />
      <path d="m9 12 2.2 2.2L15.5 10" />
    </Icon>
  );
}

/** Disbursement: funds released to the customer's account. */
export function DisbursementIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="2.5" y="6" width="19" height="12" rx="2.5" />
      <circle cx="12" cy="12" r="2.6" />
      <path d="M6 10v4M18 10v4" />
    </Icon>
  );
}

/** Repayment and collection: the money coming back on a cycle. */
export function RepaymentIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M20.5 12a8.5 8.5 0 0 1-14.6 5.9L3.5 15.5" />
      <path d="M3.5 12a8.5 8.5 0 0 1 14.6-5.9l2.4 2.4" />
      <path d="M20.5 4v4.5H16M3.5 20v-4.5H8" />
    </Icon>
  );
}

/** DPD and NPL: the loans that need attention. */
export function NplIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M10.3 4.2 2.5 18a2 2 0 0 0 1.7 3h15.6a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0Z" />
      <path d="M12 9.5v4.2M12 17.2h.01" />
    </Icon>
  );
}

export function UserIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
    </Icon>
  );
}

export function LockIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
      <path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7" />
    </Icon>
  );
}

export function EyeIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z" />
      <circle cx="12" cy="12" r="2.8" />
    </Icon>
  );
}

export function EyeOffIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M9.9 5.1A9.6 9.6 0 0 1 12 4.9c6 0 9.5 6.2 9.5 6.2a17 17 0 0 1-3.3 4" />
      <path d="M6.4 6.5A16.8 16.8 0 0 0 2.5 11.1s3.5 6.2 9.5 6.2a9.7 9.7 0 0 0 4-.85" />
      <path d="m9.9 9.2a3.4 3.4 0 0 0 4.7 4.8" />
      <path d="m3 2.6 18.4 18.4" />
    </Icon>
  );
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4.5 12h14" />
      <path d="m13 6.5 5.5 5.5-5.5 5.5" />
    </Icon>
  );
}

/** Credit: the score a decision is graded against. */
export function CreditIcon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M3.5 16.5a9 9 0 1 1 17 0" />
      <path d="m12 12.5 4.2-3.4" />
      <circle cx="12" cy="16.5" r="1.4" />
    </Icon>
  );
}
