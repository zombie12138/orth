export interface MenuItem {
  id: number;
  parentId: number;
  name: string;
  type: number;
  permission: string;
  url: string;
  icon: string;
  order: number;
  status: number;
  children: MenuItem[];
}
