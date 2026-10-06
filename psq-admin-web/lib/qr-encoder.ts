// Minimal QR Code encoder (byte mode, versions 1-6, error correction M).
//
// The console has to hand a phone a URL it can open, and typing an IP address
// on a phone keyboard is the main friction point. Rendering a real QR code
// removes it. This is a compact, dependency-free implementation of ISO/IEC
// 18004: finder/timing/alignment patterns, Reed-Solomon error correction,
// block interleaving, best-mask selection and the 15-bit format information.
//
// Versions 1-6 in byte mode at level M hold 14-106 bytes, which covers every
// LAN URL this console can produce (`http://255.255.255.255:65535/` is 30).

export type QrCode = {
  /** Side length in modules. */
  size: number;
  /** One entry per row; true marks a dark module. */
  modules: boolean[][];
};

// Data codewords and error-correction codewords per block, indexed by
// version - 1, for error correction level M. Derived from the symbol total
// codeword counts and the level-M block layout of ISO/IEC 18004.
const DATA_CODEWORDS = [16, 28, 44, 64, 86, 108];
const EC_CODEWORDS_PER_BLOCK = [10, 16, 26, 18, 24, 16];
const EC_BLOCKS = [1, 1, 1, 2, 2, 4];

const MAX_VERSION = DATA_CODEWORDS.length;

// Galois field GF(2^8) with the QR primitive polynomial x^8+x^4+x^3+x^2+1.
const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
{
  let value = 1;
  for (let index = 0; index < 255; index += 1) {
    EXP[index] = value;
    LOG[value] = index;
    value <<= 1;
    if (value & 0x100) value ^= 0x11d;
  }
  for (let index = 255; index < 512; index += 1) {
    EXP[index] = EXP[index - 255];
  }
}

function gfMultiply(left: number, right: number): number {
  if (left === 0 || right === 0) return 0;
  return EXP[LOG[left] + LOG[right]];
}

/** Product of two polynomials with coefficients in GF(256). */
function polyMultiply(left: number[], right: number[]): number[] {
  const product = Array.from({ length: left.length + right.length - 1 }, () => 0);
  for (let i = 0; i < left.length; i += 1) {
    for (let j = 0; j < right.length; j += 1) {
      product[i + j] ^= gfMultiply(left[i], right[j]);
    }
  }
  return product;
}

/** Reed-Solomon generator polynomial of the requested degree. */
function generatorPolynomial(degree: number): number[] {
  let generator = [1];
  for (let index = 0; index < degree; index += 1) {
    generator = polyMultiply(generator, [1, EXP[index]]);
  }
  return generator;
}

/** Remainder of `data` divided by `divisor`, i.e. the error correction words. */
function errorCorrection(data: number[], divisor: number[]): number[] {
  const remainder = Array.from({ length: divisor.length - 1 }, () => 0);
  for (const codeword of data) {
    const factor = codeword ^ remainder[0];
    remainder.shift();
    remainder.push(0);
    for (let index = 0; index < remainder.length; index += 1) {
      remainder[index] ^= gfMultiply(divisor[index + 1], factor);
    }
  }
  return remainder;
}

/** Total codewords for a version, minus the level-M data codewords. */
function interleave(
  data: number[],
  version: number,
): number[] {
  const blockCount = EC_BLOCKS[version - 1];
  const ecPerBlock = EC_CODEWORDS_PER_BLOCK[version - 1];
  const totalData = DATA_CODEWORDS[version - 1];
  const shortBlockLength = Math.floor(totalData / blockCount);
  const longBlockCount = totalData % blockCount;
  const divisor = generatorPolynomial(ecPerBlock);

  const dataBlocks: number[][] = [];
  const ecBlocks: number[][] = [];
  let offset = 0;
  for (let block = 0; block < blockCount; block += 1) {
    // The longer blocks come first, matching the specification's block order.
    const length = shortBlockLength + (block < longBlockCount ? 1 : 0);
    const chunk = data.slice(offset, offset + length);
    offset += length;
    dataBlocks.push(chunk);
    ecBlocks.push(errorCorrection(chunk, divisor));
  }

  const result: number[] = [];
  const longestData = shortBlockLength + (longBlockCount > 0 ? 1 : 0);
  for (let index = 0; index < longestData; index += 1) {
    for (const block of dataBlocks) {
      if (index < block.length) result.push(block[index]);
    }
  }
  for (let index = 0; index < ecPerBlock; index += 1) {
    for (const block of ecBlocks) {
      result.push(block[index]);
    }
  }
  return result;
}

function bitsToCodewords(bits: number[]): number[] {
  const codewords = Array.from({ length: bits.length >> 3 }, () => 0);
  for (let index = 0; index < bits.length; index += 1) {
    codewords[index >> 3] |= bits[index] << (7 - (index & 7));
  }
  return codewords;
}

function encodeToCodewords(bytes: number[], version: number): number[] {
  const bits: number[] = [];
  const push = (value: number, length: number) => {
    for (let shift = length - 1; shift >= 0; shift -= 1) {
      bits.push((value >> shift) & 1);
    }
  };

  push(0b0100, 4); // Byte mode.
  push(bytes.length, version < 10 ? 8 : 16);
  for (const byte of bytes) push(byte, 8);

  const capacity = DATA_CODEWORDS[version - 1] * 8;
  // Terminator, then zero bits up to the next codeword boundary.
  push(0, Math.min(4, capacity - bits.length));
  push(0, (8 - (bits.length & 7)) & 7);
  // Alternating padding codewords until the data capacity is full.
  for (let pad = 0xec; bits.length < capacity; pad ^= 0xec ^ 0x11) {
    push(pad, 8);
  }

  return interleave(bitsToCodewords(bits), version);
}

function alignmentPositions(version: number): number[] {
  if (version === 1) return [];
  const count = Math.floor(version / 7) + 2;
  const step =
    version === 32
      ? 26
      : Math.ceil((version * 4 + 4) / (count * 2 - 2)) * 2;
  const positions = [6];
  for (let index = count - 1; index >= 1; index -= 1) {
    positions.splice(1, 0, version * 4 + 10 - (count - 1 - index) * step);
  }
  return positions;
}

function createBaseMatrix(version: number): (boolean | null)[][] {
  const size = version * 4 + 17;
  const matrix: (boolean | null)[][] = Array.from({ length: size }, () =>
    Array.from({ length: size }, () => null),
  );

  const setFinder = (top: number, left: number) => {
    for (let row = -1; row <= 7; row += 1) {
      for (let column = -1; column <= 7; column += 1) {
        const y = top + row;
        const x = left + column;
        if (y < 0 || y >= size || x < 0 || x >= size) continue;
        const inside =
          row >= 0 && row <= 6 && column >= 0 && column <= 6;
        const dark =
          inside &&
          (row === 0 ||
            row === 6 ||
            column === 0 ||
            column === 6 ||
            (row >= 2 && row <= 4 && column >= 2 && column <= 4));
        matrix[y][x] = dark;
      }
    }
  };

  setFinder(0, 0);
  setFinder(0, size - 7);
  setFinder(size - 7, 0);

  // Timing patterns.
  for (let index = 8; index < size - 8; index += 1) {
    const dark = index % 2 === 0;
    if (matrix[6][index] === null) matrix[6][index] = dark;
    if (matrix[index][6] === null) matrix[index][6] = dark;
  }

  // Alignment patterns are placed at every combination of the two centre
  // coordinates except where they would collide with a finder pattern: any
  // centre on row 6 or column 6 sits on a timing line and is skipped, which
  // covers all three finder corners.
  const positions = alignmentPositions(version);
  for (const row of positions) {
    for (const column of positions) {
      if (row === 6 || column === 6) continue;
      for (let dy = -2; dy <= 2; dy += 1) {
        for (let dx = -2; dx <= 2; dx += 1) {
          matrix[row + dy][column + dx] =
            Math.max(Math.abs(dy), Math.abs(dx)) !== 1;
        }
      }
    }
  }

  // Reserve only the format information modules and the always-dark module.
  // Everything else in these rows and columns is real data area.
  for (let index = 0; index <= 8; index += 1) {
    if (matrix[8][index] === null) matrix[8][index] = false;
    if (matrix[index][8] === null) matrix[index][8] = false;
  }
  for (let index = 0; index < 8; index += 1) {
    if (matrix[8][size - 1 - index] === null) {
      matrix[8][size - 1 - index] = false;
    }
  }
  for (let index = 0; index < 7; index += 1) {
    if (matrix[size - 1 - index][8] === null) {
      matrix[size - 1 - index][8] = false;
    }
  }
  matrix[size - 8][8] = true;

  return matrix;
}

type Placement = { row: number; column: number };

/**
 * True for every module that belongs to a function pattern and therefore never
 * carries data: the three finders with their separators, the timing patterns,
 * the alignment patterns, the format information and the always-dark module.
 */
function isFunctionModule(version: number, row: number, column: number): boolean {
  const size = version * 4 + 17;
  // Finder patterns plus separators.
  if (row <= 7 && column <= 7) return true;
  if (row <= 7 && column >= size - 8) return true;
  if (row >= size - 8 && column <= 7) return true;
  // Timing patterns.
  if (row === 6 || column === 6) return true;
  // Alignment patterns; the same centre rule as the base matrix (any centre on
  // row 6 or column 6 is skipped because it would sit on a timing line).
  const positions = alignmentPositions(version);
  for (const center of positions) {
    if (center === 6) continue;
    for (const other of positions) {
      if (other === 6) continue;
      if (Math.abs(row - center) <= 2 && Math.abs(column - other) <= 2) {
        return true;
      }
    }
  }
  // Format information and the always-dark module.
  if (row === 8 && (column <= 8 || column >= size - 8)) return true;
  if (column === 8 && (row <= 8 || row >= size - 8)) return true;
  if (row === size - 8 && column === 8) return true;
  return false;
}

/**
 * Every data module in the zigzag order defined by the specification. The
 * traversal is a single continuous boustrophedon: `row` and `direction` carry
 * over from one column pair to the next, so an upward pass ends at the top and
 * the following pair starts there and moves down. Function modules are skipped
 * rather than merely overwritten, which keeps the bit stream aligned with what
 * a decoder expects.
 */
function dataPlacements(version: number): Placement[] {
  const size = version * 4 + 17;
  const placements: Placement[] = [];
  let direction = -1;
  let row = size - 1;

  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5; // The vertical timing column is skipped.
    while (row >= 0 && row < size) {
      for (const column of [right, right - 1]) {
        if (isFunctionModule(version, row, column)) continue;
        placements.push({ row, column });
      }
      row += direction;
    }
    // Step back inside the symbol and reverse direction for the next pair.
    row -= direction;
    direction = -direction;
  }
  return placements;
}

function drawData(
  matrix: boolean[][],
  codewords: number[],
  placements: Placement[],
): void {
  const bits: number[] = [];
  for (const codeword of codewords) {
    for (let shift = 7; shift >= 0; shift -= 1) bits.push((codeword >> shift) & 1);
  }
  // Unused trailing modules stay light, which is the padding the spec expects.
  for (let index = 0; index < placements.length; index += 1) {
    const { row, column } = placements[index];
    matrix[row][column] = index < bits.length && bits[index] === 1;
  }
}

function maskApplies(mask: number, row: number, column: number): boolean {
  switch (mask) {
    case 0:
      return (row + column) % 2 === 0;
    case 1:
      return row % 2 === 0;
    case 2:
      return column % 3 === 0;
    case 3:
      return (row + column) % 3 === 0;
    case 4:
      return (Math.floor(row / 2) + Math.floor(column / 3)) % 2 === 0;
    case 5:
      return ((row * column) % 2) + ((row * column) % 3) === 0;
    case 6:
      return (((row * column) % 2) + ((row * column) % 3)) % 2 === 0;
    default:
      return (((row + column) % 2) + ((row * column) % 3)) % 2 === 0;
  }
}

/**
 * The 15 format bits for one mask, most significant bit first, and the matrix
 * coordinates they are written to. Both copies of the format information must
 * carry the identical bit sequence.
 */
function formatBits(mask: number): number[] {
  // Error correction level M is 0b00; the 5 data bits are level then mask.
  const data = (0b00 << 3) | mask;
  let remainder = data;
  for (let index = 0; index < 10; index += 1) {
    remainder = (remainder << 1) ^ ((remainder >> 9) * 0x537);
  }
  const bits = ((data << 10) | remainder) ^ 0x5412;
  return Array.from({ length: 15 }, (_, index) => (bits >> (14 - index)) & 1);
}

type FormatTarget = { row: number; column: number; bit: number };

/** Where each format bit goes, split into the two redundant copies. */
function formatTargets(size: number): [FormatTarget[], FormatTarget[]] {
  const first: FormatTarget[] = [];
  for (let bit = 0; bit <= 5; bit += 1) first.push({ row: 8, column: bit, bit });
  first.push({ row: 8, column: 7, bit: 6 });
  first.push({ row: 8, column: 8, bit: 7 });
  first.push({ row: 7, column: 8, bit: 8 });
  for (let bit = 9; bit < 15; bit += 1) {
    first.push({ row: 14 - bit, column: 8, bit });
  }

  const second: FormatTarget[] = [];
  for (let bit = 0; bit < 7; bit += 1) {
    second.push({ row: size - 1 - bit, column: 8, bit });
  }
  // Bits 7-14 run rightwards along row 8, sharing its last cell with the dark
  // module position rule used by every other encoder.
  for (let bit = 7; bit < 15; bit += 1) {
    second.push({ row: 8, column: size - 8 + (bit - 7), bit });
  }

  return [first, second];
}

function drawFormatBits(matrix: boolean[][], mask: number): void {
  const size = matrix.length;
  const bits = formatBits(mask);
  for (const copy of formatTargets(size)) {
    for (const { row, column, bit } of copy) {
      matrix[row][column] = bits[bit] === 1;
    }
  }
  matrix[size - 8][8] = true;
}

/**
 * Applies one of the eight data masks. Function patterns (finders, separators,
 * timing, alignment, format information and the always-dark module) must stay
 * untouched, so the mask is only applied where the base matrix reserved a data
 * slot. `drawFormatBits` writes the format information afterwards.
 */
function applyMask(matrix: boolean[][], mask: number, version: number): boolean[][] {
  return matrix.map((row, y) =>
    row.map((dark, x) =>
      isFunctionModule(version, y, x)
        ? dark
        : dark !== maskApplies(mask, y, x),
    ),
  );
}
const PENALTY_RUN = 3;
const PENALTY_BLOCK = 3;
const PENALTY_FINDER = 40;
const PENALTY_BALANCE = 10;

function linePenalty(line: boolean[]): number {
  let score = 0;
  let runLength = 1;
  for (let index = 1; index <= line.length; index += 1) {
    if (index < line.length && line[index] === line[index - 1]) {
      runLength += 1;
      continue;
    }
    if (runLength >= 5) score += PENALTY_RUN + (runLength - 5);
    runLength = 1;
  }
  return score;
}

function finderPenalty(line: boolean[]): number {
  let score = 0;
  const length = line.length;
  for (let index = 0; index + 6 < length; index += 1) {
    if (
      line[index] &&
      !line[index + 1] &&
      line[index + 2] &&
      line[index + 3] &&
      line[index + 4] &&
      !line[index + 5] &&
      line[index + 6] &&
      // The 1:1:3:1:1 core plus four light modules on at least one side.
      ((index >= 4 &&
        !line[index - 1] &&
        !line[index - 2] &&
        !line[index - 3] &&
        !line[index - 4]) ||
        (index + 10 < length &&
          !line[index + 7] &&
          !line[index + 8] &&
          !line[index + 9] &&
          !line[index + 10]))
    ) {
      score += PENALTY_FINDER;
      index += 6;
    }
  }
  return score;
}

function penaltyScore(matrix: boolean[][]): number {
  const size = matrix.length;
  let score = 0;

  for (const row of matrix) {
    score += linePenalty(row) + finderPenalty(row);
  }
  for (let column = 0; column < size; column += 1) {
    const line = matrix.map((row) => row[column]);
    score += linePenalty(line) + finderPenalty(line);
  }
  for (let row = 0; row + 1 < size; row += 1) {
    for (let column = 0; column + 1 < size; column += 1) {
      const dark = matrix[row][column];
      if (
        matrix[row][column + 1] === dark &&
        matrix[row + 1][column] === dark &&
        matrix[row + 1][column + 1] === dark
      ) {
        score += PENALTY_BLOCK;
      }
    }
  }

  let darkCount = 0;
  for (const row of matrix) {
    for (const dark of row) if (dark) darkCount += 1;
  }
  const total = size * size;
  const deviation = Math.abs(darkCount * 20 - total * 10);
  score += Math.floor(deviation / total) * PENALTY_BALANCE;

  return score;
}

/** Smallest version whose level-M byte capacity holds `bytes.length`. */
function versionForBytes(length: number): number {
  for (let candidate = 1; candidate <= MAX_VERSION; candidate += 1) {
    // 4 bits of mode + 8 bits of byte count, then the payload.
    const capacityBits = DATA_CODEWORDS[candidate - 1] * 8;
    if (12 + 8 * length <= capacityBits) return candidate;
  }
  return 0;
}

/**
 * Encodes `text` as a QR code, or returns null when it does not fit in
 * version 6 at error correction level M (106 bytes).
 */
export function createQrCode(text: string): QrCode | null {
  const bytes = Array.from(new TextEncoder().encode(text));
  const version = versionForBytes(bytes.length);
  if (!version) return null;

  const base = createBaseMatrix(version);
  const placements = dataPlacements(version);
  const codewords = encodeToCodewords(bytes, version);

  let best: boolean[][] | null = null;
  let bestScore = Number.POSITIVE_INFINITY;
  for (let mask = 0; mask < 8; mask += 1) {
    const candidate = base.map((row) => row.map((dark) => dark === true));
    drawData(candidate, codewords, placements);
    const masked = applyMask(candidate, mask, version);
    drawFormatBits(masked, mask);
    const score = penaltyScore(masked);
    if (score < bestScore) {
      bestScore = score;
      best = masked;
    }
  }

  return best ? { size: best.length, modules: best } : null;
}

/**
 * Renders a QR code as a standalone SVG string. `scale` is the module size in
 * user units; the result is responsive via its viewBox.
 */
export function qrCodeSvg(
  text: string,
  options: { scale?: number; quietZone?: number; dark?: string; light?: string } = {},
): string | null {
  const code = createQrCode(text);
  if (!code) return null;
  const scale = options.scale ?? 4;
  const quietZone = options.quietZone ?? 4;
  const dark = options.dark ?? '#101828';
  const light = options.light ?? '#ffffff';
  const dimension = (code.size + quietZone * 2) * scale;

  let path = '';
  for (let row = 0; row < code.size; row += 1) {
    for (let column = 0; column < code.size; column += 1) {
      if (!code.modules[row][column]) continue;
      const x = (column + quietZone) * scale;
      const y = (row + quietZone) * scale;
      path += `M${x} ${y}h${scale}v${scale}h-${scale}z`;
    }
  }

  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${dimension}" height="${dimension}" ` +
    `viewBox="0 0 ${dimension} ${dimension}" shape-rendering="crispEdges" role="presentation">` +
    `<rect width="${dimension}" height="${dimension}" fill="${light}"/>` +
    `<path d="${path}" fill="${dark}"/></svg>`
  );
}
