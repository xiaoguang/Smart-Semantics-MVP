import { useState } from 'react';
import { Card, Button, Dropdown, Input, Select, InputNumber, DatePicker, Space, message, Modal, Tag, Drawer, Form, Empty } from 'antd';
import { PlusOutlined, EditOutlined, MoreOutlined, CalendarOutlined } from '@ant-design/icons';
import { useStore } from '../../store/useStore';
import type { HolidayCalendar, HolidayPeriod, HolidayType } from '../../types';
import dayjs from 'dayjs';
import ResponsiveDataView, { ResponsiveDataCard } from '../../components/ResponsiveDataView';

const typeLabels: Record<HolidayType, string> = { LEGAL: '法定假日', ENTERPRISE: '企业活动', CUSTOM: '自定义' };
const typeColors: Record<HolidayType, string> = { LEGAL: 'red', ENTERPRISE: 'orange', CUSTOM: 'default' };

export default function HolidayTab({ readOnly = false }: { readOnly?: boolean }) {
  const { holidayCalendars, holidayPeriods, addHolidayCalendar, updateHolidayCalendar, deleteHolidayCalendar, addHolidayPeriod, updateHolidayPeriod, deleteHolidayPeriod } = useStore();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<HolidayCalendar | null>(null);
  const [selectedHolidayId, setSelectedHolidayId] = useState<number | null>(holidayCalendars[0]?.id ?? null);
  const [form] = Form.useForm();
  const [periodOpen, setPeriodOpen] = useState(false);
  const [editingPeriod, setEditingPeriod] = useState<HolidayPeriod | null>(null);
  const [periodForm] = Form.useForm();

  const selectedHoliday = holidayCalendars.find((h) => h.id === selectedHolidayId);
  const periods = holidayPeriods.filter((p) => p.holidayCalendarId === selectedHolidayId);

  const handleNewHoliday = () => { setEditing(null); form.resetFields(); form.setFieldsValue({ holidayType: 'LEGAL', countryCode: 'CN' }); setDrawerOpen(true); };
  const handleEditHoliday = (h: HolidayCalendar) => { setEditing(h); form.setFieldsValue(h); setDrawerOpen(true); };
  const handleDeleteHoliday = (id: number) => {
    const hasPeriods = holidayPeriods.some((p) => p.holidayCalendarId === id);
    if (hasPeriods) { message.error('该节假日已有年度日期数据，请先删除年度数据'); return; }
    deleteHolidayCalendar(id);
    if (selectedHolidayId === id) setSelectedHolidayId(holidayCalendars.find((h) => h.id !== id)?.id ?? null);
    message.success('删除成功');
  };
  const renderHolidayActions = (holiday: HolidayCalendar) => readOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : (
    <Space size={0}>
      <Button size="small" icon={<EditOutlined />} onClick={() => handleEditHoliday(holiday)}>编辑</Button>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [{ key: 'delete', label: '删除', danger: true }],
          onClick: () => Modal.confirm({
            title: `删除节假日“${holiday.holidayName}”？`,
            okText: '删除',
            okButtonProps: { danger: true },
            cancelText: '取消',
            onOk: () => handleDeleteHoliday(holiday.id),
          }),
        }}
      >
        <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${holiday.holidayName}`} />
      </Dropdown>
    </Space>
  );
  const handleSaveHoliday = () => {
    form.validateFields().then((values) => {
      if (editing) { updateHolidayCalendar(editing.id, values); message.success('更新成功'); }
      else {
        if (holidayCalendars.find((h) => h.holidayCode === values.holidayCode)) { message.error('编码已存在'); return; }
        addHolidayCalendar({ ...values, status: 'ACTIVE' }); message.success('创建成功');
      }
      setDrawerOpen(false);
    });
  };

  const handleAddPeriod = () => {
    if (!selectedHolidayId) return;
    setEditingPeriod(null);
    periodForm.resetFields();
    periodForm.setFieldsValue({ year: dayjs().year(), dateRange: [] });
    setPeriodOpen(true);
  };

  const handleEditPeriod = (period: HolidayPeriod) => {
    setEditingPeriod(period);
    periodForm.setFieldsValue({
      year: period.year,
      dateRange: [dayjs(period.startDate), dayjs(period.endDate)],
      workdayAdjustmentsText: period.workdayAdjustments.map((item) => `${item.date} ${item.description}`).join('\n'),
    });
    setPeriodOpen(true);
  };
  const renderPeriodActions = (period: HolidayPeriod) => readOnly ? <span style={{ color: '#8c8c8c' }}>只读</span> : (
    <Space size={0}>
      <Button size="small" onClick={() => handleEditPeriod(period)}>编辑</Button>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [{ key: 'delete', label: '删除年度数据', danger: true }],
          onClick: () => Modal.confirm({
            title: '删除此年度数据？',
            okText: '删除',
            okButtonProps: { danger: true },
            cancelText: '取消',
            onOk: () => deleteHolidayPeriod(period.id),
          }),
        }}
      >
        <Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多操作：${period.year}`} />
      </Dropdown>
    </Space>
  );

  const handleSavePeriod = () => {
    if (!selectedHolidayId) return;
    periodForm.validateFields().then((values) => {
      const [start, end] = values.dateRange;
      if (end.isBefore(start, 'day')) { message.error('结束日期不能早于开始日期'); return; }
      if (periods.some((period) => period.id !== editingPeriod?.id && period.year === values.year)) { message.error('该年份已存在'); return; }
      const workdayAdjustments = String(values.workdayAdjustmentsText ?? '').split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
        const [date, ...description] = line.split(/\s+/);
        return { date, description: description.join(' ') || '调休工作日' };
      });
      const next = { holidayCalendarId: selectedHolidayId, year: values.year, startDate: start.format('YYYY-MM-DD'), endDate: end.format('YYYY-MM-DD'), workdayAdjustments };
      if (editingPeriod) updateHolidayPeriod(editingPeriod.id, next);
      else addHolidayPeriod({ id: 0, ...next });
      message.success(editingPeriod ? '年度日期已更新' : '年度日期已添加');
      setPeriodOpen(false);
    });
  };

  const holidayColumns = [
    { title: '', key: 'select', width: 40, render: (_: unknown, r: HolidayCalendar) => (
      r.id === selectedHolidayId ? <span style={{ color: '#1677ff', fontSize: 16 }}>●</span> : <span style={{ color: '#d9d9d9', fontSize: 16, cursor: 'pointer' }} onClick={() => setSelectedHolidayId(r.id)}>○</span>
    )},
    { title: '节假日名称', dataIndex: 'holidayName', key: 'name', width: 240, render: (v: string, r: HolidayCalendar) => (
      <span style={{ cursor: 'pointer', color: r.id === selectedHolidayId ? '#1677ff' : 'inherit', fontWeight: r.id === selectedHolidayId ? 600 : 400 }} onClick={() => setSelectedHolidayId(r.id)}>{v}</span>
    )},
    { title: '编码', dataIndex: 'holidayCode', key: 'code', width: 190, render: (v: string) => <code>{v}</code> },
    { title: '类型', dataIndex: 'holidayType', key: 'type', width: 140, render: (v: HolidayType) => <Tag color={typeColors[v]}>{typeLabels[v]}</Tag> },
    { title: '国家', dataIndex: 'countryCode', key: 'country', width: 100, render: (v: string) => v || '—' },
    { title: '年度覆盖', key: 'coverage', width: 120, render: (_: unknown, r: HolidayCalendar) => {
      const count = holidayPeriods.filter((p) => p.holidayCalendarId === r.id).length;
      return count > 0 ? <Tag color="green">{count} 年</Tag> : <Tag color="default">未配置</Tag>;
    }},
    { title: '操作', key: 'action', width: 126, render: (_: unknown, r: HolidayCalendar) => renderHolidayActions(r) },
  ];

  const periodColumns = [
    { title: '年份', dataIndex: 'year', key: 'year', width: 100 },
    { title: '开始日期', dataIndex: 'startDate', key: 'start', width: 150 },
    { title: '结束日期', dataIndex: 'endDate', key: 'end', width: 150 },
    { title: '调休工作日', key: 'adjust', render: (_: unknown, r: HolidayPeriod) => (
      r.workdayAdjustments.length > 0
        ? r.workdayAdjustments.map((w, i) => <Tag key={i}>{w.date} {w.description}</Tag>)
        : <span style={{ color: '#999' }}>无</span>
    )},
    { title: '操作', key: 'action', width: 126, render: (_: unknown, r: HolidayPeriod) => renderPeriodActions(r) },
  ];

  return (
    <>
      <Card size="small" title="节假日定义" style={{ marginBottom: 16 }} extra={!readOnly && <Button type="primary" size="small" icon={<PlusOutlined />} onClick={handleNewHoliday}>新建节假日</Button>}>
        <ResponsiveDataView
          ariaLabel="节假日列表"
          dataSource={holidayCalendars}
          columns={holidayColumns}
          rowKey="id"
          minTableWidth={960}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          tableProps={{ size: 'small', onRow: (holiday) => ({ style: { background: holiday.id === selectedHolidayId ? '#e6f4ff' : undefined, cursor: 'pointer' }, onClick: () => setSelectedHolidayId(holiday.id) }) }}
          renderCard={(holiday) => {
            const coverage = holidayPeriods.filter((period) => period.holidayCalendarId === holiday.id).length;
            return <ResponsiveDataCard
              title={holiday.holidayName}
              subtitle={<code>{holiday.holidayCode}</code>}
              status={<Tag color={typeColors[holiday.holidayType]}>{typeLabels[holiday.holidayType]}</Tag>}
              selected={holiday.id === selectedHolidayId}
              fields={[{ label: '国家', value: holiday.countryCode || '—' }, { label: '年度覆盖', value: coverage > 0 ? `${coverage} 年` : '未配置' }]}
              actions={<Space size={4}><Button size="small" onClick={() => setSelectedHolidayId(holiday.id)}>查看年度</Button>{renderHolidayActions(holiday)}</Space>}
            />;
          }}
        />
      </Card>

      <Card size="small" title={selectedHoliday ? <span><CalendarOutlined /> {selectedHoliday.holidayName} — 年度日期</span> : '请选择节假日'} extra={selectedHoliday && !readOnly && <Button size="small" icon={<PlusOutlined />} onClick={handleAddPeriod}>添加年度</Button>}>
        {selectedHoliday ? (
          <>
            <ResponsiveDataView
              ariaLabel="节假日年度日期列表"
              dataSource={periods}
              columns={periodColumns}
              rowKey="id"
              minTableWidth={860}
              pagination={{ pageSize: 20, showSizeChanger: false }}
              tableProps={{ size: 'small', bordered: true }}
              renderCard={(period) => <ResponsiveDataCard
                title={`${period.year} 年`}
                fields={[
                  { label: '开始日期', value: period.startDate },
                  { label: '结束日期', value: period.endDate },
                  { label: '调休工作日', value: period.workdayAdjustments.length > 0 ? period.workdayAdjustments.map((item) => <Tag key={`${item.date}-${item.description}`}>{item.date} {item.description}</Tag>) : '无', wide: true },
                ]}
                actions={renderPeriodActions(period)}
              />}
            />
            {periods.length === 0 && <Empty description="暂无年度日期数据，点击「添加年度」录入" style={{ padding: 20 }} />}
          </>
        ) : (
          <Empty description="请先在上方表格中选择或新建一个节假日" />
        )}
      </Card>

      <Drawer title={editing ? '编辑节假日定义' : '新建节假日定义'} width="min(480px, 100vw)" open={drawerOpen} onClose={() => setDrawerOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" onClick={handleSaveHoliday}>保存</Button></Space>}>
        <Form form={form} layout="vertical">
          <Form.Item name="holidayName" label="节假日名称" rules={[{ required: true }]}><Input placeholder="如：春节、双十一" /></Form.Item>
          <Form.Item name="holidayCode" label="节假日编码" rules={[{ required: true }]} extra="空间内唯一，创建后不可改"><Input placeholder="如：SPRING_FEST、DOUBLE_11" disabled={!!editing} /></Form.Item>
          <Form.Item name="holidayType" label="类型" rules={[{ required: true }]}>
            <Select options={[{ value: 'LEGAL', label: '法定假日' }, { value: 'ENTERPRISE', label: '企业活动' }, { value: 'CUSTOM', label: '自定义' }]} />
          </Form.Item>
          <Form.Item name="countryCode" label="所属国家"><Select allowClear options={[{ value: 'CN', label: '中国' }, { value: 'US', label: '美国' }, { value: 'JP', label: '日本' }]} /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Drawer>
      <Drawer title={editingPeriod ? '编辑年度日期' : '添加年度日期'} width="min(480px, 100vw)" open={periodOpen} onClose={() => setPeriodOpen(false)}
        footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={() => setPeriodOpen(false)}>取消</Button><Button type="primary" onClick={handleSavePeriod}>保存</Button></Space>}>
        <Form form={periodForm} layout="vertical">
          <Form.Item name="year" label="年份" rules={[{ required: true }]}><InputNumber min={2020} max={2035} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="dateRange" label="起止日期" rules={[{ required: true, message: '请选择完整日期范围' }]}><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="workdayAdjustmentsText" label="调休工作日" extra="每行一条：日期 空格 说明"><Input.TextArea rows={4} placeholder={'2026-02-14 春节调休上班'} /></Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
