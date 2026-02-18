import { useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Tag,
  message,
  Popconfirm,
  Form,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { fetchGroups, deleteGroup } from '../../api/groups';
import { usePagination } from '../../hooks/usePagination';
import { formatDate } from '../../utils/date';
import type { XxlJobGroup } from '../../types/group';
import GroupFormModal from './components/GroupFormModal';

export default function ExecutorGroupsPage() {
  const queryClient = useQueryClient();
  const { current, pageSize, offset, onChange } = usePagination();
  const [appname, setAppname] = useState('');
  const [title, setTitle] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<XxlJobGroup | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['groups', offset, pageSize, appname, title],
    queryFn: () =>
      fetchGroups({
        offset,
        pagesize: pageSize,
        appname: appname || undefined,
        title: title || undefined,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteGroup,
    onSuccess: () => {
      message.success('Group deleted');
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
    onError: (e) => message.error(e.message),
  });

  const columns: ColumnsType<XxlJobGroup> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'AppName', dataIndex: 'appname', width: 150 },
    { title: 'Title', dataIndex: 'title', width: 150 },
    {
      title: 'Type',
      dataIndex: 'addressType',
      width: 90,
      render: (v: number) => (
        <Tag color={v === 0 ? 'blue' : 'orange'}>
          {v === 0 ? 'Auto' : 'Manual'}
        </Tag>
      ),
    },
    {
      title: 'Online Machines',
      width: 200,
      render: (_: unknown, r: XxlJobGroup) => {
        const list =
          r.addressType === 0
            ? r.registryList ?? []
            : (r.addressList ?? '').split(',').filter(Boolean);
        return list.length > 0 ? list.join(', ') : '-';
      },
    },
    {
      title: 'Updated',
      dataIndex: 'updateTime',
      width: 160,
      render: (v: string) => formatDate(v),
    },
    {
      title: 'Actions',
      width: 100,
      render: (_: unknown, record: XxlJobGroup) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingGroup(record);
              setFormOpen(true);
            }}
          />
          <Popconfirm
            title="Delete this group?"
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card>
        <Form layout="inline" style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
          <Form.Item>
            <Input
              placeholder="AppName"
              allowClear
              onChange={(e) => setAppname(e.target.value)}
            />
          </Form.Item>
          <Form.Item>
            <Input
              placeholder="Title"
              allowClear
              onChange={(e) => setTitle(e.target.value)}
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingGroup(null);
                setFormOpen(true);
              }}
            >
              Add Group
            </Button>
          </Form.Item>
        </Form>

        <Table<XxlJobGroup>
          rowKey="id"
          columns={columns}
          dataSource={data?.data}
          loading={isLoading}
          scroll={{ x: 800 }}
          pagination={{
            current,
            pageSize,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (total) => `Total ${total}`,
            onChange,
          }}
        />
      </Card>

      <GroupFormModal
        open={formOpen}
        group={editingGroup}
        onClose={() => {
          setFormOpen(false);
          setEditingGroup(null);
        }}
        onSuccess={() =>
          queryClient.invalidateQueries({ queryKey: ['groups'] })
        }
      />
    </>
  );
}
