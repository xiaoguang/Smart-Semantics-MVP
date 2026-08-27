import { useState } from 'react';
import { Button, Drawer, Dropdown, Form, Input, Select, InputNumber, message, Space, Modal, Tag } from 'antd';
import { PlusOutlined, EditOutlined, MoreOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { TimeCalendar, CalendarType } from '../../types';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';

const typeLabels: Record<CalendarType, string> = { NATURAL: '自然年', FISCAL: '财年', RETAIL_445: '零售445', CUSTOM: '自定义' };
const typeColors: Record<CalendarType, string> = { NATURAL: 'green', FISCAL: 'blue', RETAIL_445: 'orange', CUSTOM: 'default' };

export default function CalendarTab({ readOnly = false }: { readOnly?: boolean }) {
  const { calendars, addCalendar, updateCalendar, deleteCalendar, rules } = useStore();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TimeCalendar | null>(null);
  const [form] = Form.useForm();

  const handleNew = () => { setEditing(null); form.resetFields(); form.setFieldsValue({ calendarType: 'NATURAL', yearStartMonth: 1, yearStartDay: 1 }); setOpen(true); };
  const handleEdit = (r: TimeCalendar) => { setEditing(r); form.setFieldsValue(r); setOpen(true); };
  const handleDelete = (id: number) => {
    const used = rules.filter((r) => r.calendarCode === calendars.find((c) => c.id === id)?.calendarCode);
    if (used.length > 0) { message.error(`该日历被 ${used.length} 条时间规则引用，无法删除`); return; }
    deleteCalendar(id); message.success('删除成功');
  };
  const renderCalendarActions = (calendar: TimeCalendar) => readOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : (
    <Space size={0}>
      <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(calendar)}>编辑</Button>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [{ key: 'delete', label: '删除', danger: true }],
          onClick: () => Modal.confirm({
            title: `删除日历“${calendar.calendarName}”？`,
            okText: '删除',
            okButtonProps: { danger: true },
            cancelText: '取消',
            onOk: () => handleDelete(calendar.id),
          }),
        }}
      >
        <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${calendar.calendarName}`} />
      </Dropdown>
    </Space>
  );

  const handleSave = () => {
    form.validateFields().then((values) => {
      if (editing) { updateCalendar(editing.id, values); message.success('更新成功'); }
      else {
        if (calendars.find((c) => c.calendarCode === values.calendarCode)) { message.error('编码已存在'); return; }
        addCalendar({ ...values, status: 'ACTIVE' }); message.success('创建成功');
      }
      setOpen(false);
    });
  };

  const calType = Form.useWatch('calendarType', form);

  const columns = [
    { title: '日历名称', dataIndex: 'calendarName', key: 'name', width: 240 },
    { title: '编码', dataIndex: 'calendarCode', key: 'code', width: 190, render: (v: string) => <code>{v}</code> },
    { title: '类型', dataIndex: 'calendarType', key: 'type', width: 140, render: (v: CalendarType) => <Tag color={typeColors[v]}>{typeLabels[v]}</Tag> },
    { title: '年起始月', dataIndex: 'yearStartMonth', key: 'month', width: 120, render: (v: number) => `${v}月` },
    { title: '关联规则', key: 'rules', width: 120, render: (_: unknown, r: TimeCalendar) => rules.filter((rule) => rule.calendarCode === r.calendarCode).length + ' 条' },
    { title: '操作', key: 'action', width: 126, render: (_: unknown, r: TimeCalendar) => renderCalendarActions(r) },
  ];

  return (
    <>
      {!readOnly && <div style={{ marginBottom: 16 }}><Button type="primary" icon={<PlusOutlined />} onClick={handleNew}>新建日历</Button></div>}
      <ResponsiveDataView
        ariaLabel="时间日历列表"
        dataSource={calendars}
        columns={columns}
        rowKey="id"
        minTableWidth={940}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        tableProps={{ size: 'small' }}
        renderCard={(calendar) => <ResponsiveDataCard
          title={calendar.calendarName}
          subtitle={<code>{calendar.calendarCode}</code>}
          status={<Tag color={typeColors[calendar.calendarType]}>{typeLabels[calendar.calendarType]}</Tag>}
          fields={[
            { label: '年起始月', value: `${calendar.yearStartMonth}月` },
            { label: '关联规则', value: `${rules.filter((rule) => rule.calendarCode === calendar.calendarCode).length} 条` },
            ...(calendar.description ? [{ label: '说明', value: calendar.description, wide: true }] : []),
          ]}
          actions={renderCalendarActions(calendar)}
        />}
      />

      <Drawer title={editing ? '编辑日历' : '新建日历'} width="min(480px, 100vw)" open={open} onClose={() => setOpen(false)} footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" onClick={handleSave}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <Form.Item name="calendarName" label="日历名称" rules={[{ required: true }]}><Input placeholder="如：自然日历、财年日历(4月起始)" /></Form.Item>
          <Form.Item name="calendarCode" label="日历编码" rules={[{ required: true }]} extra="空间内唯一，建议使用英文"><Input placeholder="如：natural、fiscal_04" disabled={!!editing} /></Form.Item>
          <Form.Item name="calendarType" label="日历类型" rules={[{ required: true }]}><Select options={[{ value: 'NATURAL', label: '自然年' }, { value: 'FISCAL', label: '财年' }, { value: 'RETAIL_445', label: '零售445' }, { value: 'CUSTOM', label: '自定义' }]} /></Form.Item>
          <Form.Item name="yearStartMonth" label="年起始月份"><InputNumber min={1} max={12} disabled={calType === 'NATURAL'} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="yearStartDay" label="年起始日"><InputNumber min={1} max={31} disabled={calType === 'NATURAL'} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
