import type { FormulaExpression, FormulaOperator, FormulaToken } from './mockData';

export function formatFormulaTokens(tokens: FormulaToken[]) {
  return tokens.map((token) => token.value).join(' ');
}

export function parseFormulaTokens(tokens: FormulaToken[]): { expression?: FormulaExpression; error?: string } {
  let cursor = 0;

  const parsePrimary = (): FormulaExpression => {
    const token = tokens[cursor];
    if (!token) throw new Error('表达式不能以运算符结束');
    if (token.type === 'METRIC') {
      if (!token.metricId || !token.metricCode) throw new Error('存在无法识别的指标');
      cursor += 1;
      return { type: 'METRIC', metricId: token.metricId, metricCode: token.metricCode };
    }
    if (token.type === 'NUM') {
      const value = Number(token.value);
      if (!Number.isFinite(value)) throw new Error(`数字“${token.value}”格式不正确`);
      cursor += 1;
      return { type: 'NUMBER', value };
    }
    if (token.type === 'LPAREN') {
      cursor += 1;
      const expression = parseExpression();
      if (tokens[cursor]?.type !== 'RPAREN') throw new Error('括号未闭合');
      cursor += 1;
      return expression;
    }
    if (token.type === 'RPAREN') throw new Error('存在多余的右括号');
    throw new Error(`运算符“${token.value}”前缺少指标、数字或括号表达式`);
  };

  const parseTerm = (): FormulaExpression => {
    let left = parsePrimary();
    while (tokens[cursor]?.type === 'OP' && ['*', '/'].includes(tokens[cursor].value)) {
      const operator = tokens[cursor].value as FormulaOperator;
      cursor += 1;
      const right = parsePrimary();
      if (operator === '/' && right.type === 'NUMBER' && right.value === 0) throw new Error('除数不能为 0');
      left = { type: 'BINARY', operator, left, right };
    }
    return left;
  };

  const parseExpression = (): FormulaExpression => {
    let left = parseTerm();
    while (tokens[cursor]?.type === 'OP' && ['+', '-'].includes(tokens[cursor].value)) {
      const operator = tokens[cursor].value as FormulaOperator;
      cursor += 1;
      left = { type: 'BINARY', operator, left, right: parseTerm() };
    }
    return left;
  };

  if (tokens.length === 0) return { error: '请编排计算公式' };
  try {
    const expression = parseExpression();
    if (cursor < tokens.length) {
      const token = tokens[cursor];
      throw new Error(token.type === 'RPAREN' ? '存在多余的右括号' : `“${token.value}”前缺少运算符`);
    }
    return { expression };
  } catch (error) {
    return { error: error instanceof Error ? error.message : '公式格式不正确' };
  }
}
