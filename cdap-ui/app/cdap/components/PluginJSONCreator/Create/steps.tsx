/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import ConfigurationGroupPage from 'components/PluginJSONCreator/Create/Content/ConfigurationGroupPage';
import FilterPage from 'components/PluginJSONCreator/Create/Content/FilterPage';
import OutputPage from 'components/PluginJSONCreator/Create/Content/OutputPage';
import PluginInfoPage from 'components/PluginJSONCreator/Create/Content/PluginInfoPage';

export const STEPS = [
  {
    label: 'Plugin Information',
    component: PluginInfoPage,
  },
  {
    label: 'Configuration Groups',
    component: ConfigurationGroupPage,
  },
  {
    label: 'Output',
    component: OutputPage,
  },
  {
    label: 'Filters',
    component: FilterPage,
  },
];