import { Fragment, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Alert, Button, Empty, Select, Typography } from 'antd';
import { ClearOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { FormulaExpression, FormulaOperator, FormulaToken, SemanticMetric } from './mockData';
import { formatFormulaTokens, parseFormulaTokens } from './formulaUtils';
import { semanticObjectVisual } from '../../components/semantic-object-visuals';
import './CompositeFormulaBuilder.css';

const { Text } = Typography;
const operators: FormulaOperator[] = ['+', '-', '*', '/'];
const numberKeys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '.'];

interface CompositeFormulaBuilderProps {
  metrics: SemanticMetric[];
  value: FormulaToken[];
  onChange: (tokens: FormulaToken[], formula: string, expression?: FormulaExpression) => void;
}

export default function CompositeFormulaBuilder({ metrics, value, onChange }: CompositeFormulaBuilderProps) {
  const metricVisual = semanticObjectVisual('METRIC');
  const initialIds = [...new Set(value.flatMap((token) => token.type === 'METRIC' && token.metricId ? [token.metricId] : []))];
  const [selectedMetricIds, setSelectedMetricIds] = useState<number[]>(initialIds);
  const [cursorIndex, setCursorIndex] = useState(value.length);
  const selectedMetrics = metrics.filter((metric) => selectedMetricIds.includes(metric.id));
  const parseResult = useMemo(() => parseFormulaTokens(value), [value]);

  useEffect(() => {
    setCursorIndex((current) => Math.min(current, value.length));
  }, [value.length]);

  const update = (tokens: FormulaToken[], nextCursorIndex = cursorIndex) => {
    const result = parseFormulaTokens(tokens);
    setCursorIndex(Math.min(nextCursorIndex, tokens.length));
    onChange(tokens, formatFormulaTokens(tokens), result.expression);
  };

  const insertToken = (token: FormulaToken) => {
    update([...value.slice(0, cursorIndex), token, ...value.slice(cursorIndex)], cursorIndex + 1);
  };
  const insertMetric = (metric: SemanticMetric) => insertToken({
    type: 'METRIC', value: metric.metricCode, metricId: metric.id, metricCode: metric.metricCode, metricName: metric.metricName,
  });
  const insertOperator = (operator: FormulaOperator) => insertToken({ type: 'OP', value: operator });
  const insertParenthesis = (parenthesis: '(' | ')') => insertToken({ type: parenthesis === '(' ? 'LPAREN' : 'RPAREN', value: parenthesis });
  const insertNumber = (key: string) => {
    const previous = value[cursorIndex - 1];
    if (previous?.type === 'NUM') {
      if (key === '.' && previous.value.includes('.')) return;
      const nextValue = previous.value === '0' && key !== '.' ? key : `${previous.value}${key}`;
      update(value.map((token, index) => index === cursorIndex - 1 ? { ...previous, value: nextValue } : token));
      return;
    }
    insertToken({ type: 'NUM', value: key === '.' ? '0.' : key });
  };
  const removeBeforeCursor = () => {
    if (cursorIndex === 0) return;
    const previous = value[cursorIndex - 1];
    if (previous?.type === 'NUM' && previous.value.length > 1) {
      update(value.map((token, index) => index === cursorIndex - 1 ? { ...previous, value: previous.value.slice(0, -1) } : token));
    } else {
      update([...value.slice(0, cursorIndex - 1), ...value.slice(cursorIndex)], cursorIndex - 1);
    }
  };

  return (
    <div className="composite-formula-builder" style={{ '--metric-fill': metricVisual.fill, '--metric-stroke': metricVisual.stroke } as CSSProperties}>
      <aside className="formula-source-panel">
        <div className="formula-panel-heading">
          <div>
            <Text strong>参与计算的原子指标</Text>
            <Text type="secondary">先选择指标，再点击插入公式</Text>
          </div>
          <span className="formula-count">{selectedMetrics.length}</span>
        </div>
        <Select
          mode="multiple"
          value={selectedMetricIds}
          onChange={setSelectedMetricIds}
          options={metrics.map((metric) => ({ value: metric.id, label: `${metric.metricName}（${metric.metricCode}）` }))}
          placeholder="选择已保存的原子指标"
          className="formula-source-select"
          maxTagCount="responsive"
          optionFilterProp="label"
          showSearch
        />
        <div className="formula-source-list">
          {selectedMetrics.length ? (
            <>
              {selectedMetrics.map((metric) => (
                <Button key={metric.id} className="formula-source-item" block onClick={() => insertMetric(metric)}>
                  <span className="formula-source-copy">
                    <strong>{metric.metricName}</strong>
                    <small>{metric.metricCode}</small>
                  </span>
                  <PlusOutlined className="formula-source-add" />
                </Button>
              ))}
            </>
          ) : <Empty className="formula-source-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择原子指标" />}
        </div>
      </aside>

      <section className="formula-editor-panel">
        <div className="formula-panel-heading formula-editor-heading">
          <div>
            <Text strong>公式编排</Text>
            <Text type="secondary">点击公式中的任意位置可移动插入光标</Text>
          </div>
        </div>
        <div className="formula-keyboard-row">
          <span className="formula-keyboard-label">计算符号</span>
          <div className="formula-keyboard-keys">
            {operators.map((operator) => <Button key={operator} className="formula-key formula-key-operator" onClick={() => insertOperator(operator)}>{operator}</Button>)}
            <Button className="formula-key formula-key-operator" onClick={() => insertParenthesis('(')}>(</Button>
            <Button className="formula-key formula-key-operator" onClick={() => insertParenthesis(')')}>)</Button>
          </div>
        </div>
        <div className="formula-keyboard-row">
          <span className="formula-keyboard-label">数字键盘</span>
          <div className="formula-keyboard-keys">
            {numberKeys.map((key) => <Button key={key} className="formula-key" onClick={() => insertNumber(key)}>{key}</Button>)}
          </div>
        </div>
        <div className="formula-expression-panel">
          <div className="formula-expression-toolbar">
            <div>
              <Text strong>计算表达式</Text>
              <span className="formula-cursor-legend"><i />蓝色竖线为插入位置</span>
            </div>
            <div className="formula-expression-actions">
              <Button type="text" size="small" icon={<DeleteOutlined />} disabled={cursorIndex === 0} onClick={removeBeforeCursor}>退格</Button>
              <Button type="text" size="small" icon={<ClearOutlined />} disabled={!value.length} onClick={() => update([], 0)}>清空</Button>
            </div>
          </div>
          <div className={`formula-expression-canvas${value.length ? '' : ' is-empty'}`}>
            {value.length ? (
              <div className="formula-token-stream">
                {value.map((token, index) => (
                  <Fragment key={`${index}-${token.type}-${token.value}`}>
                    <FormulaCursor active={cursorIndex === index} onClick={() => setCursorIndex(index)} />
                    <button
                      type="button"
                      title="点击将光标移到此项之后"
                      onClick={() => setCursorIndex(index + 1)}
                      className={`formula-token formula-token-${token.type.toLowerCase()}`}
                    >
                      {token.type === 'METRIC' ? <><strong>{token.metricName || token.metricCode}</strong><small>{token.metricCode}</small></> : token.value}
                    </button>
                  </Fragment>
                ))}
                <FormulaCursor active={cursorIndex === value.length} onClick={() => setCursorIndex(value.length)} />
              </div>
            ) : <div className="formula-empty-copy"><span>ƒ</span><Text type="secondary">从左侧插入指标，再使用计算符号完成公式</Text></div>}
          </div>
        </div>
        <Alert className="formula-validation" type={parseResult.error ? 'warning' : 'success'} showIcon message={parseResult.error || '公式结构正确，可以保存'} />
      </section>
    </div>
  );
}

function FormulaCursor({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label="公式插入位置"
      onClick={onClick}
      className={`formula-insert-cursor${active ? ' is-active' : ''}`}
    />
  );
}
