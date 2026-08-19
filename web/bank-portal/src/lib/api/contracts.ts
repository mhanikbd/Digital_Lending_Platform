import { z } from "zod";

/**
 * Runtime shapes of the backend contract.
 *
 * The portal validates every response at the boundary instead of trusting a
 * TypeScript type that only exists at compile time. A backend that changes shape
 * then produces a clear, localised failure rather than an undefined deep inside
 * a component. These schemas are hand-written for Milestone 1; once more
 * endpoints exist they are generated from the OpenAPI document the backend
 * publishes at /v3/api-docs.
 */

export const apiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  violations: z
    .array(z.object({ field: z.string(), message: z.string() }))
    .optional(),
});

/** Wraps a payload schema in the standard ApiResponse envelope. */
export function envelopeOf<T extends z.ZodTypeAny>(payload: T) {
  return z.object({
    success: z.boolean(),
    // Absent rather than null: the backend omits null properties.
    data: payload.optional(),
    error: apiErrorSchema.optional(),
    correlationId: z.string().optional(),
    timestamp: z.string().optional(),
  });
}

export const componentStatusSchema = z.object({
  name: z.string(),
  status: z.enum(["UP", "DOWN"]),
  detail: z.string(),
});

export const platformHealthSchema = z.object({
  status: z.enum(["UP", "DOWN"]),
  components: z.array(componentStatusSchema),
});

export const platformInfoSchema = z.object({
  application: z.string(),
  apiVersion: z.string(),
  environment: z.string(),
  serverTime: z.string(),
});

/* ---- Authentication ---------------------------------------------------- */

export const authenticatedUserSchema = z.object({
  id: z.string(),
  username: z.string(),
  displayName: z.string(),
  userType: z.string(),
  mustChangeSecret: z.boolean(),
  lastLoginAt: z.string().optional(),
  roles: z.array(z.string()).default([]),
  permissions: z.array(z.string()).default([]),
});

export const tokenPairSchema = z.object({
  accessToken: z.string(),
  expiresInSeconds: z.number(),
  refreshToken: z.string(),
  refreshExpiresInSeconds: z.number(),
});

/**
 * One endpoint, two outcomes. The client branches on "status" rather than on
 * which fields happen to be present, which is why the backend sends it.
 */
export const loginResponseSchema = z.object({
  status: z.enum(["AUTHENTICATED", "MFA_REQUIRED"]),
  tokens: tokenPairSchema.optional(),
  user: authenticatedUserSchema.optional(),
  mfaChallengeId: z.string().optional(),
  mfaExpiresInSeconds: z.number().optional(),
});

/* ---- Organisation ------------------------------------------------------ */

/**
 * A unit and its children. Recursive, so the schema needs an explicit
 * annotation: zod cannot infer a type that refers to itself.
 */
export type OrgUnit = {
  id: string;
  code: string;
  name: string;
  unitType: string;
  status: string;
  city?: string;
  district?: string;
  children: OrgUnit[];
};

export const orgUnitSchema: z.ZodType<OrgUnit> = z.lazy(() =>
  z.object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    unitType: z.string(),
    status: z.string(),
    city: z.string().optional(),
    district: z.string().optional(),
    children: z.array(orgUnitSchema).default([]),
  }),
);

export const orgUnitTreeSchema = z.array(orgUnitSchema);

export const orgScopeSchema = z.object({
  scopeLevel: z.string(),
  postings: z.array(
    z.object({
      code: z.string(),
      name: z.string(),
      unitType: z.string(),
      primary: z.boolean(),
    }),
  ).default([]),
  visibleUnitCodes: z.array(z.string()).default([]),
});

export type OrgScope = z.infer<typeof orgScopeSchema>;

/* ---- Customers --------------------------------------------------------- */

export const customerSummarySchema = z.object({
  customerId: z.string(),
  fullName: z.string(),
  customerType: z.string(),
  mobile: z.string(),
  branchCode: z.string().optional(),
  branchName: z.string().optional(),
  riskProfile: z.string(),
  kycStatus: z.string(),
  status: z.string(),
});

export const customerListSchema = z.array(customerSummarySchema);

export type CustomerSummary = z.infer<typeof customerSummarySchema>;

/* ---- Products ---------------------------------------------------------- */

/**
 * Money and rates arrive as strings, not numbers.
 *
 * <p>The backend serialises every decimal that way on purpose: JSON numbers are
 * parsed into 64-bit floats by JavaScript, which loses paisa. The portal keeps
 * them as strings and never does arithmetic on them - the backend is
 * authoritative for every figure shown here.
 */
const decimal = z.string();

export const productFeeSchema = z.object({
  code: z.string(),
  name: z.string(),
  calculationMethod: z.string(),
  flatAmount: decimal.optional(),
  rate: decimal.optional(),
  vatRate: decimal,
  collectedAt: z.string(),
  mandatory: z.boolean(),
});

export const productRiskLimitSchema = z.object({
  riskProfile: z.string(),
  maxAmount: decimal,
});

export const productVersionSchema = z.object({
  id: z.string(),
  versionNo: z.number(),
  status: z.string(),
  effectiveFrom: z.string(),
  effectiveTo: z.string().optional(),
  customerSegment: z.string(),
  secured: z.boolean(),
  currency: z.string(),
  minAmount: decimal,
  maxAmount: decimal,
  tenures: z.array(z.number()).default([]),
  interestMethod: z.string(),
  interestRate: decimal,
  repaymentFrequency: z.string(),
  gracePeriodDays: z.number(),
  collateralRequired: z.boolean(),
  guarantorRequired: z.boolean(),
  incomeMultiple: decimal.optional(),
  maxDbr: decimal.optional(),
  regulatoryMaxAmount: decimal.optional(),
  recommendedRatio: decimal,
  maxTotalExposure: decimal.optional(),
  fees: z.array(productFeeSchema).default([]),
  riskLimits: z.array(productRiskLimitSchema).default([]),
});

export const productSummarySchema = z.object({
  code: z.string(),
  name: z.string(),
  nameBn: z.string().optional(),
  productType: z.string(),
  category: z.string(),
  description: z.string().optional(),
  status: z.string(),
  versionCount: z.number(),
  currentVersion: productVersionSchema.optional(),
});

export const productListSchema = z.array(productSummarySchema);

export const productDetailSchema = z.object({
  code: z.string(),
  name: z.string(),
  nameBn: z.string().optional(),
  productType: z.string(),
  category: z.string(),
  description: z.string().optional(),
  status: z.string(),
  currentVersion: productVersionSchema.optional(),
  versions: z.array(productVersionSchema).default([]),
});

/* ---- Quotation --------------------------------------------------------- */

export const quoteFeeSchema = z.object({
  code: z.string(),
  name: z.string(),
  calculationMethod: z.string(),
  rate: decimal.optional(),
  amount: decimal,
  vatRate: decimal,
  vat: decimal,
  total: decimal,
  collectedAt: z.string(),
});

export const instalmentSchema = z.object({
  number: z.number(),
  amountDue: decimal,
  principal: decimal,
  interest: decimal,
  closingBalance: decimal,
});

export const loanQuoteSchema = z.object({
  productCode: z.string(),
  productName: z.string(),
  productVersion: z.number(),
  currency: z.string(),
  principal: decimal,
  tenureMonths: z.number(),
  instalments: z.number(),
  repaymentFrequency: z.string(),
  interestRate: decimal,
  interestMethod: z.string(),
  rateNegotiated: z.boolean(),
  instalment: decimal,
  totalInterest: decimal,
  totalFees: decimal,
  totalVat: decimal,
  totalPayable: decimal,
  netDisbursement: decimal,
  fees: z.array(quoteFeeSchema).default([]),
  schedule: z.array(instalmentSchema).default([]),
});

/* ---- Eligibility ------------------------------------------------------- */

export const ruleLineSchema = z.object({
  attribute: z.string(),
  attributeName: z.string(),
  criterion: z.string(),
  actualValue: z.string().optional(),
  passed: z.boolean(),
  message: z.string().optional(),
});

export const ruleGroupResultSchema = z.object({
  code: z.string(),
  name: z.string(),
  logic: z.string(),
  passed: z.boolean(),
  message: z.string().optional(),
  criteria: z.array(ruleLineSchema).default([]),
});

export const limitFactorSchema = z.object({
  code: z.string(),
  name: z.string(),
  amount: decimal.optional(),
  binding: z.boolean(),
  explanation: z.string(),
});

export const amountDecisionSchema = z.object({
  maxAmount: decimal,
  recommendedAmount: decimal,
  bindingFactor: z.string(),
  belowMinimum: z.boolean(),
  factors: z.array(limitFactorSchema).default([]),
});

export const eligibilitySchema = z.object({
  eligible: z.boolean(),
  customerId: z.string(),
  productCode: z.string(),
  productName: z.string(),
  productVersion: z.number(),
  currency: z.string(),
  maxAmount: decimal.optional(),
  recommendedAmount: decimal.optional(),
  availableTenures: z.array(z.number()).default([]),
  interestRate: decimal,
  interestMethod: z.string(),
  riskGrade: z.string().optional(),
  reasons: z.array(z.string()).default([]),
  criteria: z.array(ruleGroupResultSchema).default([]),
  limits: amountDecisionSchema.optional(),
  evaluationId: z.string().optional(),
});

export type ProductSummary = z.infer<typeof productSummarySchema>;
export type ProductVersion = z.infer<typeof productVersionSchema>;
export type ProductDetail = z.infer<typeof productDetailSchema>;
export type LoanQuote = z.infer<typeof loanQuoteSchema>;
export type Eligibility = z.infer<typeof eligibilitySchema>;
export type RuleGroupResult = z.infer<typeof ruleGroupResultSchema>;
export type LimitFactor = z.infer<typeof limitFactorSchema>;

export type ApiError = z.infer<typeof apiErrorSchema>;
export type AuthenticatedUser = z.infer<typeof authenticatedUserSchema>;
export type TokenPair = z.infer<typeof tokenPairSchema>;
export type LoginResponse = z.infer<typeof loginResponseSchema>;
export type ComponentStatus = z.infer<typeof componentStatusSchema>;
export type PlatformHealth = z.infer<typeof platformHealthSchema>;
export type PlatformInfo = z.infer<typeof platformInfoSchema>;
